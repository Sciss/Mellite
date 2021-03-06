/*
 *  ProcActions.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.lucre.{BooleanObj, Copy, DoubleObj, Folder, IntObj, LongObj, Obj, SpanLikeObj, StringObj, Txn, expr}
import de.sciss.{equal, proc}
import de.sciss.proc.{AudioCue, ObjKeys, Proc, Timeline}
import de.sciss.span.Span

object ProcActions {
  private val MinDur    = 32

  // scalac still has bug finding Timeline.Modifiable
  // private type TimelineMod[T <: Txn[T]] = Timeline.Modifiable[T] // BiGroup.Modifiable[T, Proc[T], Proc.Update[T]]
  private type TimelineMod[T <: Txn[T]] = Timeline.Modifiable[T]

  final case class Resize(deltaStart: Long, deltaStop: Long, deltaTrackStart: Int = 0, deltaTrackStop: Int = 0) {
    override def toString = s"$productPrefix(deltaStart = $deltaStart, deltaStop = $deltaStop, deltaTrackStart = $deltaTrackStart, deltaTrackStop = $deltaTrackStop)"
  }

  final case class Move(deltaTime : Long, deltaTrack: Int, copy: Boolean) {
    override def toString = s"$productPrefix(deltaTime = $deltaTime, deltaTrack = $deltaTrack, copy = $copy)"
  }

  /** Queries the audio region's grapheme segment start and audio element. */
  def getAudioRegion[T <: Txn[T]](proc: Proc[T])(implicit tx: T): Option[AudioCue.Obj[T]] =
    proc.attr.$[AudioCue.Obj](Proc.graphAudio)

  def resize[T <: Txn[T]](span: SpanLikeObj[T], obj: Obj[T],
                          amount: Resize, minStart: Long /* timelineModel: TimelineModel */)
                         (implicit tx: T): Unit = {
    import amount._

    require (amount.deltaTrackStart == 0 && amount.deltaTrackStop == 0, "Track position resize not supported")

    val oldSpan   = span.value
    // val minStart  = timelineModel.bounds.start
    val dStartC   = if (deltaStart >= 0) deltaStart else oldSpan match {
      case Span.HasStart(oldStart)  => math.max(-(oldStart - minStart)         , deltaStart)
      case _ => 0L
    }
    val dStopC   = if (deltaStop >= 0) deltaStop else oldSpan match {
      case Span.HasStop (oldStop)   => math.max(-(oldStop  - minStart + MinDur), deltaStop)
      case _ => 0L
    }

    if (dStartC != 0L || dStopC != 0L) {

      val (dStartCC, dStopCC) = (dStartC, dStopC)

      span match {
        case SpanLikeObj.Var(s) =>
          val oldSpan = s()
          val newSpan = {
            oldSpan.value match {
              case Span.From (start)  => Span.From (start + dStartCC)
              case Span.Until(stop )  => Span.Until(stop  + dStopCC )
              case Span(start, stop)  =>
                val newStart = start + dStartCC
                Span(newStart, math.max(newStart + MinDur, stop + dStopCC))
              case other => other
            }
          }
          s() = newSpan

        case _ =>
      }

      if (dStartCC != 0L) obj match {
        case objT: Proc[T] =>
          for {
            audioCue <- getAudioRegion[T](objT)
          } {
            def any2stringadd: Any = ()
            audioCue match {
              case AudioCue.Obj.Shift(peer, amt) =>
                import expr.Ops._
                amt match {
                  case LongObj.Var(amtVr) =>
                    amtVr() = amtVr() + dStartCC
                  case _ =>
                    val newCue = AudioCue.Obj.Shift(peer, LongObj.newVar[T](amt + dStartCC))
                    objT.attr.put(Proc.graphAudio, newCue)
                }
              case other =>
                val newCue = AudioCue.Obj.Shift(other, LongObj.newVar[T](dStartCC))
                objT.attr.put(Proc.graphAudio, newCue)
            }
          }
        case _ =>
      }
    }
  }

  /** Changes or removes the name of a process.
    *
    * @param obj the proc to rename
    * @param name the new name or `None` to remove the name attribute
    */
  def rename[T <: Txn[T]](obj: Obj[T], name: Option[String])(implicit tx: T): Unit = {
    val attr  = obj.attr
    name match {
      case Some(n) =>
        attr.$[StringObj](ObjKeys.attrName) match {
          case Some(StringObj.Var(vr)) => vr() = n
          case _                  => attr.put(ObjKeys.attrName, StringObj.newVar(n))
        }

      case _ => attr.remove(ObjKeys.attrName)
    }
  }

  /** Makes a copy of a proc. Copies the graph and all attributes,
    * except for `Proc.mainIn`
    *
    * @param in  the process to copy
    */
  def copy[T <: Txn[T]](in: Obj[T])(implicit tx: T): Obj[T] = copy(in, connectInput = false)

    /** Makes a copy of a proc. Copies the graph and all attributes.
    * If `connect` is falls, does not put `Proc.mainIn`
    *
    * @param in  the process to copy
    */
  def copy[T <: Txn[T]](in: Obj[T], connectInput: Boolean)(implicit tx: T): Obj[T] = {
    val context = Copy.apply[T, T]()
    val out     = context.copyPlain(in)
    val attrIn  = in .attr
    val attrOut = out.attr
    attrIn.iterator.foreach { case (key, valueIn) =>
      import equal.Implicits._
      if (key !== Proc.mainIn) {
        val valueOut = context(valueIn)
        attrOut.put(key, valueOut)
      } else if (connectInput) {
        val valueOpt = attrIn.get(Proc.mainIn).collect {
          case op: Proc.Output[T] => op
          case fIn: Folder[T] =>
            val fOut = Folder[T]()
            fIn.iterator.foreach { op => fOut.addLast(op) }
            fOut
        }
        valueOpt.foreach(value => attrOut.put(Proc.mainIn, value))
      }
    }
    context.finish()
    out
  }

  def setGain[T <: Txn[T]](proc: Proc[T], gain: Double)(implicit tx: T): Unit = {
    val attr  = proc.attr

    if (gain == 1.0) {
      attr.remove(ObjKeys.attrGain)
    } else {
      attr.$[DoubleObj](ObjKeys.attrGain) match {
        case Some(DoubleObj.Var(vr)) => vr() = gain
        case _                  => attr.put(ObjKeys.attrGain, DoubleObj.newVar(gain))
      }
    }
  }

  def adjustGain[T <: Txn[T]](obj: Obj[T], factor: Double)(implicit tx: T): Unit = {
    if (factor == 1.0) return

    val attr  = obj.attr

    attr.$[DoubleObj](ObjKeys.attrGain) match {
      case Some(DoubleObj.Var(vr)) =>
        import expr.Ops._
        vr() = vr() * factor
      case other =>
        val newGain = other.fold(1.0)(_.value) * factor
        attr.put(ObjKeys.attrGain, DoubleObj.newVar(newGain))
    }
  }

  def setBus[T <: Txn[T]](objects: Iterable[Obj[T]], intExpr: IntObj[T])(implicit tx: T): Unit = {
    objects.foreach { proc =>
      proc.attr.put(ObjKeys.attrBus, intExpr)
    }
  }

  def toggleMute[T <: Txn[T]](obj: Obj[T])(implicit tx: T): Unit = {
    val attr = obj.attr
    attr.$[BooleanObj](ObjKeys.attrMute) match {
      // XXX TODO: BooleanObj should have `not` operator
      case Some(BooleanObj.Var(vr)) => 
        val old   = vr()
        val vOld  = old.value
        vr()      = !vOld
      case _ => attr.put(ObjKeys.attrMute, BooleanObj.newVar(true))
    }
  }

  def mkAudioRegion[T <: Txn[T]](time      : Span,
                                 audioCue  : AudioCue.Obj[T],
                                 gOffset   : Long)
     (implicit tx: T): (SpanLikeObj /* SpanObj */[T], Proc[T]) = {

    // val srRatio = grapheme.spec.sampleRate / Timeline.SampleRate
    val spanV   = time // Span(time, time + (selection.length / srRatio).toLong)
    val span    = SpanLikeObj /* SpanObj */.newVar[T](spanV)
    val p       = Proc[T]()
    val a       = p.attr

    // val scanIn  = proc.inputs .add(Proc.graphAudio )
    /*val sOut=*/ p.outputs.add(Proc.mainOut)
    // val grIn    = Grapheme[T] //(audioCue.value.spec.numChannels)

    // we preserve data.source(), i.e. the original audio file offset
    // ; therefore the grapheme element must start `selection.start` frames
    // before the insertion position `drop.frame`

    // require(gOffset == 0L, s"mkAudioRegion - don't know yet how to handle cue offset time")
    import proc.Ops._
    // This is tricky. Ideally we would keep `audioCue.offset`,
    // but then editing heuristics become a nightmare. Therefore,
    // now just flatten the thing...

//    val gOffset1    = (gOffset: LongObj[T]) + audioCue.offset
//    val audioCueOff = audioCue.replaceOffset(LongObj.newVar(gOffset1))
//    val gOffset1    = gOffset + audioCue.value.offset
//    val audioCueOff = audioCue.replaceOffset(LongObj.newVar(gOffset1))

    // XXX TODO -- cheese louise, the heuristics will get nasty
    val audioCueOff = if (gOffset == 0L) audioCue else {
      audioCue match {
        case AudioCue.Obj.Shift(peer, amt) =>
          AudioCue.Obj.Shift(peer , LongObj.newVar[T](amt + gOffset))
        case other =>
          AudioCue.Obj.Shift(other, LongObj.newVar[T](      gOffset))
      }
    }

    // val gStart  = LongObj.newVar(time - selection.start)
    // require(time.start == 0, s"mkAudioRegion - don't know yet how to handle relative grapheme time")
    // val gStart = LongObj.newVar[T](-gOffset)
    // val bi: Grapheme.TimedElem[T] = (gStart, grapheme) // BiExpr(gStart, grapheme)
    // grIn.add(gStart, audioCue)
    // scanIn add grIn
    p.graph() = Proc.GraphObj.tape
    a.put(Proc.graphAudio, audioCueOff /* grIn */)
    a.put(Proc.attrSource, Proc.GraphObj.tapeSource)
    (span, p)
  }

  /** Inserts a new audio region proc into a given group.
    *
    * @param group      the group to insert the proc into
    * @param time       the time span on the outer timeline
    * @param audioCue   the grapheme carrying the underlying audio file
    * @param gOffset    the selection start with respect to the grapheme.
    *                   This is inside the underlying audio file (but using timeline sample-rate),
    *                   whereas the proc will be placed in the group aligned with `time`.
    * @return           a tuple consisting of the span expression and the newly created proc.
    */
  def insertAudioRegion[T <: Txn[T]](group     : TimelineMod[T],
                                     time      : Span,
                                     audioCue  : AudioCue.Obj[T],
                                     gOffset   : Long)
                                    (implicit tx: T): (SpanLikeObj /* SpanObj */[T], Proc[T]) = {
    val res @ (span, obj) = mkAudioRegion(time, audioCue, gOffset)
    group.add(span, obj)
    res
  }

  def insertGlobalRegion[T <: Txn[T]](
      group     : TimelineMod[T],
      name      : String,
      bus       : Option[IntObj[T]]) // Source[T, Element.Int[T]]])
     (implicit tx: T): Proc[T] = {

    val proc    = Proc[T]()
    val obj     = proc // Obj(Proc.Elem(proc))
    val attr    = obj.attr
    val nameEx  = StringObj.newVar[T](StringObj.newConst(name))
    attr.put(ObjKeys.attrName, nameEx)

    group.add(Span.All, obj) // constant span expression
    obj
  }
}