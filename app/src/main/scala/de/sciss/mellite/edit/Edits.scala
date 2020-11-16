/*
 *  Edits.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.edit

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.{BooleanObj, Cursor, DoubleObj, DoubleVector, Folder, IntObj, LongObj, Obj, SpanLikeObj, StringObj, Txn}
import de.sciss.mellite.Log.log
import de.sciss.mellite.Mellite.???!
import de.sciss.mellite.TimelineTool.{Move, Resize, Gain, Mute}
import de.sciss.mellite.{GraphemeTool, ObjTimelineView, ProcActions, TimelineView}
import de.sciss.proc.{AudioCue, Code, CurveObj, EnvSegment, Grapheme, ObjKeys, Proc, Timeline}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.SynthGraph
import de.sciss.{proc, synth}
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.util.control.NonFatal

object Edits {
//  private def any2stringadd: Any = ()

  def setBus[T <: Txn[T]](objects: Iterable[Obj[T]], intExpr: IntObj[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val name = "Set Bus"
    val edits: List[UndoableEdit] = objects.iterator.map { obj =>
      EditAttrMap.expr[T, Int, IntObj](name, obj, ObjKeys.attrBus, Some(intExpr))
    } .toList
    CompoundEdit(edits, name)
  }

  def setSynthGraph[T <: Txn[T]](processes: Iterable[Proc[T]], codeElem: Code.Obj[T])
                                (implicit tx: T, cursor: Cursor[T],
                                 compiler: Code.Compiler): Option[UndoableEdit] = {
    val code = codeElem.value
    code match {
      case csg: Code.Proc =>
        val sg = try {
          csg.execute {}  // XXX TODO: compilation blocks, not good!
        } catch {
          case NonFatal(e) =>
            e.printStackTrace()
            return None
        }

        var scanInKeys  = Set.empty[String]
        var scanOutKeys = Set.empty[String]

        sg.sources.foreach {
          case synth.proc.graph.ScanIn   (key)    => scanInKeys  += key
          case synth.proc.graph.ScanOut  (key, _) => scanOutKeys += key
          case synth.proc.graph.ScanInFix(key, _) => scanInKeys  += key
          case _ =>
        }

        // sg.sources.foreach(println)
        if (scanInKeys .nonEmpty) log.debug(s"SynthDef has the following scan in  keys: ${scanInKeys .mkString(", ")}")
        if (scanOutKeys.nonEmpty) log.debug(s"SynthDef has the following scan out keys: ${scanOutKeys.mkString(", ")}")

        val editName    = "Set Synth Graph"
        val attrNameOpt = codeElem.attr.get(ObjKeys.attrName)
        val edits       = List.newBuilder[UndoableEdit]

        processes.foreach { p =>
          val graphEx = Proc.GraphObj.newConst[T](sg)  // XXX TODO: ideally would link to code updates
          val edit1   = EditVar.Expr[T, SynthGraph, Proc.GraphObj](editName, p.graph, graphEx)
          edits += edit1
          if (attrNameOpt.nonEmpty) {
            val edit2 = EditAttrMap("Set Object Name", p, ObjKeys.attrName, attrNameOpt)
            edits += edit2
          }

          ???! // SCAN
//          def check(scans: Scans[T], keys: Set[String], isInput: Boolean): Unit = {
//            val toRemove = scans.iterator.collect {
//              case (key, scan) if !keys.contains(key) && scan.isEmpty => key
//            }
//            if (toRemove.nonEmpty) toRemove.foreach { key =>
//              edits += EditRemoveScan(p, key = key, isInput = isInput)
//            }
//
//            val existing = scans.iterator.collect {
//              case (key, _) if keys contains key => key
//            }
//            val toAdd = keys -- existing.toSet
//            if (toAdd.nonEmpty) toAdd.foreach { key =>
//              edits += EditAddScan(p, key = key, isInput = isInput)
//            }
//          }
//
//          val proc = p
//          check(proc.inputs , scanInKeys , isInput = true )
//          check(proc.outputs, scanOutKeys, isInput = false)
        }

        CompoundEdit(edits.result(), editName)

      case _ => None
    }
  }

  def setName[T <: Txn[T]](obj: Obj[T], nameOpt: Option[StringObj[T]])
                          (implicit tx: T, cursor: Cursor[T]): UndoableEdit =
    EditAttrMap.expr[T, String, StringObj]("Rename Object", obj, ObjKeys.attrName, nameOpt)

  def addLink[T <: Txn[T]](source: Proc.Output[T], sink: Proc[T], key: String = Proc.mainIn)
                          (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    log.debug(s"Link $source to $sink / $key")
    // source.addSink(Scan.Link.Scan(sink))
//    EditAddScanLink(source = source /* , sourceKey */ , sink = sink /* , sinkKey */)
    sink.attr.get(key) match {
      case Some(f: Folder[T]) =>
        val index = f.size
        EditFolderInsertObj("Link", parent = f, index = index, child = source)
      case Some(other) =>
        val f = Folder[T]()
        f.addLast(other)
        f.addLast(source)
        EditAttrMap("Add Link", obj = sink, key = key, value = Some(f))

      case None =>
        EditAttrMap("Add Link", obj = sink, key = key, value = Some(source))
    }
  }

  def removeLink[T <: Txn[T]](link: Link[T])
                             (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    log.debug(s"Unlink $link")
    val edit = link.sinkType match {
      case SinkDirect() =>
        EditAttrMap("Remove Link", obj = link.sink, key = link.key, value = None)
      case SinkFolder(f, index) =>
        EditFolderRemoveObj("Link", parent = f, index = index, child = link.source)
    }
    edit
  }

  sealed trait SinkType[T <: Txn[T]]
  final case class SinkDirect[T <: Txn[T]]() extends SinkType[T]
  final case class SinkFolder[T <: Txn[T]](f: Folder[T], index: Int) extends SinkType[T]

  final case class Link[T <: Txn[T]](source: Proc.Output[T], sink: Proc[T], key: String, sinkType: SinkType[T])

  def findLink[T <: Txn[T]](out: Proc[T], in: Proc[T], keys: ISeq[String] = Proc.mainIn :: Nil)
                           (implicit tx: T): Option[Link[T]] = {
    val attr = in.attr
    val it = out.outputs.iterator.flatMap { out =>
      keys.iterator.flatMap { key =>
        val sinkTypeOpt = attr.get(key).flatMap {
          case `out` => Some(SinkDirect[T]())
          case f: Folder[T] =>
            val idx = f.indexOf(out)
            if (idx < 0) None else Some(SinkFolder[T](f, index = idx))
          case _ => None
        }
        sinkTypeOpt.map(tpe => Link(source = out, sink = in, key = key, sinkType = tpe))
      } // .headOption
    }

    if (it.isEmpty) None else Some(it.next()) // XXX TODO -- why no `headOption` on iterator?
  }

  def linkOrUnlink[T <: Txn[T]](out: Proc[T], in: Proc[T])
                               (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    findLink(out = out, in = in).fold[Option[UndoableEdit]] {
      out.outputs.get(Proc.mainOut).map { out =>
        val key = Proc.mainIn
        addLink(source = out, sink = in, key = key)
      }
    } { link =>
      val edit = removeLink(link)
      Some(edit)
    }
  }

  def unlinkAndRemove[T <: Txn[T]](timeline: proc.Timeline.Modifiable[T], span: SpanLikeObj[T], obj: Obj[T])
                                  (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val scanEdits = obj match {
      case _: Proc[T] =>
        //        val proc  = objT
        //        // val scans = proc.scans
        //        val edits1 = proc.inputs.iterator.toList.flatMap { case (key, scan) =>
        //          scan.iterator.collect {
        //            case Scan.Link.Scan(source) =>
        //              removeLink(source, scan)
        //          }.toList
        //        }
        //        val edits2 = proc.outputs.iterator.toList.flatMap { case (key, scan) =>
        //          scan.iterator.collect {
        //            case Scan.Link.Scan(sink) =>
        //              removeLink(scan, sink)
        //          } .toList
        //        }
        //        edits1 ++ edits2
        ???! // SCAN

      case _ => Nil
    }
    val name    = "Remove Object"
    val objEdit = EditTimelineRemoveObj(name, timeline, span, obj)
    CompoundEdit(scanEdits :+ objEdit, name).get // XXX TODO - not nice, `get`
  }

  private def any2stringadd: Any = ()

  private def editAdjustIntAttr[T <: Txn[T]](obj: Obj[T], key: String, arg: Int, name: String,
                                                default: Int)(combine: (Int, Int) => Int)
                                               (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    obj.attr.$[IntObj](key) match {
      case Some(IntObj.Var(vr)) =>
        // XXX TODO could be more elaborate; for now preserve just one level of variables
        val edit = EditVar.Expr[T, Int, IntObj](name, vr, combine(vr().value, arg))
        Some(edit)
      case other =>
        import de.sciss.equal.Implicits._
        val v = combine(other.fold(default)(_.value), arg)
        val valueOpt = if (v === default) None else Some(IntObj.newVar[T](v))
        if (other.isEmpty && valueOpt.isEmpty) None else {
          val edit = EditAttrMap.expr[T, Int, IntObj](name, obj, key, valueOpt)
          Some(edit)
        }
    }

  private def editAdjustDoubleAttr[T <: Txn[T]](obj: Obj[T], key: String, arg: Double, name: String,
                                                 default: Double)(combine: (Double, Double) => Double)
                                                (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    obj.attr.$[DoubleObj](key) match {
      case Some(DoubleObj.Var(vr)) =>
        // XXX TODO could be more elaborate; for now preserve just one level of variables
        val edit = EditVar.Expr[T, Double, DoubleObj](name, vr, combine(vr().value, arg))
        Some(edit)
      case other =>
        import de.sciss.equal.Implicits._
        val v = combine(other.fold(default)(_.value), arg)
        val valueOpt = if (v === default) None else Some(DoubleObj.newVar[T](v))
        if (other.isEmpty && valueOpt.isEmpty) None else {
          val edit = EditAttrMap.expr[T, Double, DoubleObj](name, obj, key, valueOpt)
          Some(edit)
        }
    }

  private def editAdjustBooleanAttr[T <: Txn[T]](obj: Obj[T], key: String, arg: Boolean, name: String,
                                                default: Boolean)(combine: (Boolean, Boolean) => Boolean)
                                               (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    obj.attr.$[BooleanObj](key) match {
      case Some(BooleanObj.Var(vr)) =>
        // XXX TODO could be more elaborate; for now preserve just one level of variables
        val edit = EditVar.Expr[T, Boolean, BooleanObj](name, vr, combine(vr().value, arg))
        Some(edit)
      case other =>
        import de.sciss.equal.Implicits._
        val v = combine(other.fold(default)(_.value), arg)
        val valueOpt = if (v === default) None else Some(BooleanObj.newVar[T](v))
        if (other.isEmpty && valueOpt.isEmpty) None else {
          val edit = EditAttrMap.expr[T, Boolean, BooleanObj](name, obj, key, valueOpt)
          Some(edit)
        }
    }

  def gain[T <: Txn[T]](obj: Obj[T], amount: Gain)
                       (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    import amount.factor
    if (factor == 1f) None else {
      editAdjustDoubleAttr[T](obj, key = ObjKeys.attrGain, arg = factor.toDouble, name = "Adjust Gain",
        default = 1.0)(_ * _)
    }
  }

  def mute[T <: Txn[T]](obj: Obj[T], state: Mute)
                       (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    import state.engaged
    editAdjustBooleanAttr[T](obj, key = ObjKeys.attrMute, arg = engaged, name = "Adjust Mute",
      default = false)((_, now) => now)
  }

  def resize[T <: Txn[T]](span: SpanLikeObj[T], obj: Obj[T], amount: Resize, minStart: Long)
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    import de.sciss.equal.Implicits._
    var edits       = List.empty[UndoableEdit]
    val nameResize  = "Resize"

    // time
    span match {
      case SpanLikeObj.Var(vr) =>
        import amount.{deltaStart, deltaStop}
        val oldSpan   = span.value
        // val minStart  = timelineModel.bounds.start
        val dStartC   = if (deltaStart >= 0) deltaStart else oldSpan match {
          case Span.HasStart(oldStart) => math.max(-(oldStart - minStart) , deltaStart)
          case _ => 0L
        }
        val dStopC   = if (deltaStop >= 0) deltaStop else oldSpan match {
          case Span.HasStop (oldStop)   => math.max(-(oldStop  - minStart + 32 /* MinDur */), deltaStop)
          case _ => 0L
        }

        if (dStartC != 0L || dStopC != 0L) {
          // val imp = ExprImplicits[T]

          // XXX TODO -- the variable contents should ideally be looked at
          // during the edit performance

          val oldSpan = vr()
          val newSpan = oldSpan.value match {
            case Span.From (start)  => Span.From (start + dStartC)
            case Span.Until(stop )  => Span.Until(stop  + dStopC )
            case Span(start, stop)  =>
              val newStart = start + dStartC
              Span(newStart, math.max(newStart + 32 /* MinDur */, stop + dStopC))
            case other => other
          }

          val newSpanEx = SpanLikeObj.newConst[T](newSpan)
          if (newSpanEx !== oldSpan) {
            val edit0 = EditVar.Expr[T, SpanLike, SpanLikeObj](nameResize, vr, newSpanEx)
            if (dStartC != 0L) obj match {
              case objT: Proc[T] =>
                ProcActions.getAudioRegion(objT).foreach { audioCue =>
                  // Crazy heuristics
                  val edit = audioCue match {
                    case AudioCue.Obj.Shift(peer, amt) =>

                      amt match {
                        case LongObj.Var(amtVr) =>
                          // XXX TODO why we use EditVar.Expr here and EditAttrMap elsewhere?
                          // I think we should always edit the variable and not copy the contents
                          // of the variable to a new variable?
                          EditVar.Expr[T, Long, LongObj](nameResize, amtVr, amtVr() + dStartC)
                        case _ =>
                          val newCue = AudioCue.Obj.Shift(peer, LongObj.newVar[T](amt + dStartC))
                          EditAttrMap(nameResize, objT, Proc.graphAudio, Some(newCue))
                      }
                    case other =>
                      val newCue = AudioCue.Obj.Shift(other, LongObj.newVar[T](dStartC))
                      EditAttrMap(nameResize, objT, Proc.graphAudio, Some(newCue))
                  }
                  edits ::= edit
                }
              case _ =>
            }
            edits ::= edit0
          }
        }

      case _ =>
    }

    val nameTrack     = "Adjust Track Placement"
    val nameCompound  = if (edits.isEmpty) nameTrack else nameResize

    // track
    val deltaTrack = amount.deltaTrackStart
    if (deltaTrack != 0) {
      val edit = editAdjustIntAttr[T](obj, key = ObjTimelineView.attrTrackIndex, arg = deltaTrack,
        name = nameTrack, default = 0)(_ + _)
      edits :::= edit.toList
    }
    val deltaTrackH = amount.deltaTrackStop - amount.deltaTrackStart
    if (deltaTrackH != 0) {
      val edit = editAdjustIntAttr[T](obj, key = ObjTimelineView.attrTrackHeight, arg = deltaTrackH,
        name = "Adjust Track Height", default = TimelineView.DefaultTrackHeight)(_ + _)
      edits :::= edit.toList
    }

    CompoundEdit(edits, nameCompound)
  }

  def timelineMoveOrCopy[T <: Txn[T]](span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T], amount: Move, minStart: Long)
                                     (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    if (amount.copy) timelineCopyImpl(span, obj, timeline, amount, minStart = minStart)
    else             timelineMoveImpl(span, obj, timeline, amount, minStart = minStart)

  def graphemeMoveOrCopy[T <: Txn[T]](time: LongObj[T], obj: Obj[T], grapheme: Grapheme[T],
                                      amount: GraphemeTool.Move, minStart: Long)
                                     (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    if (amount.copy) ??? // graphemeCopyImpl(time, obj, grapheme, amount, minStart = minStart)
    else             graphemeMoveImpl(time, obj, grapheme, amount, minStart = minStart)

  // ---- private ----

  private def timelineCopyImpl[T <: Txn[T]](span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T], amount: Move, minStart: Long)
                                           (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    timeline.modifiableOption.map { tlMod =>
      val objCopy = ProcActions.copy[T](obj, connectInput = true)
      import amount._
      if (deltaTrack != 0) {
        val newTrack: IntObj[T] = IntObj.newVar[T](
          obj.attr.$[IntObj](ObjTimelineView.attrTrackIndex).map(_.value).getOrElse(0) + deltaTrack
        )
        objCopy.attr.put(ObjTimelineView.attrTrackIndex, newTrack)
      }

      val deltaC  = calcSpanDeltaClipped(span, amount, minStart = minStart)
      val newSpan: SpanLikeObj[T] = SpanLikeObj.newVar[T](
        span.value.shift(deltaC)
      )
      EditTimelineInsertObj("Insert Region", tlMod, newSpan, objCopy)
    }
  }

  private def calcSpanDeltaClipped[T <: Txn[T]](span: SpanLikeObj[T], amount: Move, minStart: Long)
                                               (implicit tx: T): Long = {
    import amount.deltaTime
    if (deltaTime >= 0) deltaTime else span.value match {
      case Span.HasStart(oldStart) => math.max(-(oldStart - minStart      ), deltaTime)
      case Span.HasStop (oldStop ) => math.max(-(oldStop  - minStart + 32), deltaTime)
      case _ => 0 // e.g., Span.All
    }
  }

  private def calcPosDeltaClipped[T <: Txn[T]](time: LongObj[T], amount: GraphemeTool.Move, minStart: Long)
                                               (implicit tx: T): Long = {
    import amount.deltaTime
    if (deltaTime >= 0) deltaTime else {
      val oldTime = time.value
      math.max(-(oldTime - minStart), deltaTime)
    }
  }

  private def timelineMoveImpl[T <: Txn[T]](span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T], amount: Move,
                                            minStart: Long)
                                           (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    var edits = List.empty[UndoableEdit]

    import amount._
    if (deltaTrack != 0) {
      val edit = editAdjustIntAttr[T](obj, key = ObjTimelineView.attrTrackIndex, arg = deltaTrack,
        name = "Adjust Track Placement", default = 0)(_ + _)
      edits :::= edit.toList
    }

    val name    = "Move"
    val deltaC  = calcSpanDeltaClipped(span, amount, minStart = minStart)
    if (deltaC != 0L) {
      // val imp = ExprImplicits[T]
      span match {
        case SpanLikeObj.Var(vr) =>
          // s.transform(_ shift deltaC)
//          import expr.Ops._
          val newSpan = vr() shift deltaC
          val edit    = EditVar.Expr[T, SpanLike, SpanLikeObj](name, vr, newSpan)
          edits ::= edit
        case _ =>
      }
    }

    CompoundEdit(edits, name)
  }

  private def graphemeMoveImpl[T <: Txn[T]](time: LongObj[T], value: Obj[T], grapheme: Grapheme[T],
                                            amount: GraphemeTool.Move, minStart: Long)
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    var edits = List.empty[UndoableEdit]
    val name  = "Move"

    var wasRemoved = false

    def removeOld(grMod: Grapheme.Modifiable[T]): Unit = {
      require (!wasRemoved)
      edits ::= EditGraphemeRemoveObj(name, grMod, time, value)
      wasRemoved = true
    }

    import amount._
    val newValueOpt: Option[Obj[T]] = if (deltaModelY == 0) None else {
      // in the case of const, just overwrite, in the case of
      // var, check the value stored in the var, and update the var
      // instead (recursion). otherwise, it will be some combinatorial
      // expression, and we could decide to construct a binary op instead!
      // XXX TODO -- do not hard code supported cases; should be handled by GraphemeObjView ?

      def checkDouble(objT: DoubleObj[T]): Option[DoubleObj[T]] =
        objT match {
          case DoubleObj.Var(vr) =>
            val newValue = vr() + deltaModelY
            edits ::= EditVar(name, vr, newValue)
            None
          case _ =>
            grapheme.modifiableOption.map { grMod =>
              removeOld(grMod)
              val v = objT.value + deltaModelY
              DoubleObj.newVar[T](v)  // "upgrade" to var
            }
        }

      def checkDoubleVector(objT: DoubleVector[T]): Option[DoubleVector[T]] =
        objT match {
          case DoubleVector.Var(vr) =>
            val v = vr().value.map(_ + deltaModelY)
            // val newValue = DoubleVector.newConst[T](v)
            edits ::= EditVar[T, DoubleVector[T], DoubleVector.Var[T]](name, vr, v)
            None
          case _ =>
            grapheme.modifiableOption.map { grMod =>
              removeOld(grMod)
              val v = objT.value.map(_ + deltaModelY)
              DoubleVector.newVar[T](v)  // "upgrade" to var
            }
        }

      // XXX TODO --- gosh what a horrible matching orgy
      value match {
        case objT: DoubleObj    [T] => checkDouble      (objT)
        case objT: DoubleVector [T] => checkDoubleVector(objT)

        case objT: EnvSegment.Obj[T] =>
          objT match {
            case EnvSegment.Obj.ApplySingle(start, curve) =>
              val newStartOpt = checkDouble(start)
              newStartOpt.map { newStart =>
                EnvSegment.Obj.ApplySingle(newStart, curve)
              }

            case EnvSegment.Obj.ApplyMulti(start, curve) =>
              val newStartOpt = checkDoubleVector(start)
              newStartOpt.map { newStart =>
                EnvSegment.Obj.ApplyMulti(newStart, curve)
              }

            case EnvSegment.Obj.Var(vr) =>
              val seg = vr().value
              val v = seg.startLevels match {
                case Seq(single) =>
                  EnvSegment.Single (single      + deltaModelY , seg.curve)
                case multi =>
                  EnvSegment.Multi  (multi.map(_ + deltaModelY), seg.curve)
              }
              edits ::= EditVar[T, EnvSegment.Obj[T], EnvSegment.Obj.Var[T]](name, vr, v)
              None

            case _ =>
              grapheme.modifiableOption.map { grMod =>
                removeOld(grMod)
                val seg       = objT.value
                val curve     = CurveObj.newVar[T](seg.curve)
                seg.startLevels match {
                  case Seq(single) =>
                    val singleVar = DoubleObj.newVar[T](single)
                    EnvSegment.Obj.ApplySingle(singleVar, curve)
                  case multi =>
                    val multiVar = DoubleVector.newVar[T](multi)
                    EnvSegment.Obj.ApplyMulti(multiVar, curve)
                }
              }
          }

        case _ =>
          None
      }
    }

    val deltaC  = calcPosDeltaClipped(time, amount, minStart = minStart)
    val hasDeltaTime = deltaC != 0L
    if (hasDeltaTime || newValueOpt.isDefined) {
      val newTimeOpt: Option[LongObj[T]] = time match {
        case LongObj.Var(vr) =>
//          import expr.Ops._
          val newTime = vr() + deltaC
          edits ::= EditVar.Expr[T, Long, LongObj](name, vr, newTime)
          None

        case _ if hasDeltaTime =>
          grapheme.modifiableOption.map { grMod =>
            if (newValueOpt.isEmpty) {  // wasn't removed yet
              removeOld(grMod)
            }
            val v = time.value + deltaC
            LongObj.newConst[T](v)  // "upgrade" to var
          }

        case _ =>
          None
      }

      if (newTimeOpt.isDefined || newValueOpt.isDefined) {
        val newTime   = newTimeOpt  .getOrElse(time)
        val newValue  = newValueOpt .getOrElse(value)
        grapheme.modifiableOption.foreach { grMod =>
          edits ::= EditGraphemeInsertObj(name, grMod, newTime, newValue)
        }
      }
    }

    CompoundEdit(edits.reverse, name)
  }
}