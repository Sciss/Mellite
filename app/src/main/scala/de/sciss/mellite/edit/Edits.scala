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
import de.sciss.lucre.expr.Ops._
import de.sciss.lucre.expr.{DoubleObj, DoubleVector, IntObj, LongObj, SpanLikeObj, StringObj}
import de.sciss.lucre.stm.{Folder, Obj, Sys}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.{expr, stm}
import de.sciss.mellite.Mellite.{???!, log}
import de.sciss.mellite.ProcActions.{Move, Resize}
import de.sciss.mellite.{GraphemeTool, ObjTimelineView, ProcActions}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc.{AudioCue, Code, CurveObj, EnvSegment, Grapheme, ObjKeys, Output, Proc, SynthGraphObj, Timeline}
import de.sciss.synth.{SynthGraph, proc}
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
      case csg: Code.SynthGraph =>
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
          case proc.graph.ScanIn   (key)    => scanInKeys  += key
          case proc.graph.ScanOut  (key, _) => scanOutKeys += key
          case proc.graph.ScanInFix(key, _) => scanInKeys  += key
          case _ =>
        }

        // sg.sources.foreach(println)
        if (scanInKeys .nonEmpty) log(s"SynthDef has the following scan in  keys: ${scanInKeys .mkString(", ")}")
        if (scanOutKeys.nonEmpty) log(s"SynthDef has the following scan out keys: ${scanOutKeys.mkString(", ")}")

        val editName    = "Set Synth Graph"
        val attrNameOpt = codeElem.attr.get(ObjKeys.attrName)
        val edits       = List.newBuilder[UndoableEdit]

        processes.foreach { p =>
          val graphEx = SynthGraphObj.newConst[T](sg)  // XXX TODO: ideally would link to code updates
          val edit1   = EditVar.Expr[T, SynthGraph, SynthGraphObj](editName, p.graph, graphEx)
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

  def addLink[T <: Txn[T]](source: Output[T], sink: Proc[T], key: String = Proc.mainIn)
                          (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    log(s"Link $source to $sink / $key")
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
    log(s"Unlink $link")
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

  final case class Link[T <: Txn[T]](source: Output[T], sink: Proc[T], key: String, sinkType: SinkType[T])

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

  def resize[T <: Txn[T]](span: SpanLikeObj[T], obj: Obj[T], amount: Resize, minStart: Long)
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    SpanLikeObj.Var.unapply(span).flatMap { vr =>
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

        val (dStartCC, dStopCC) = (dStartC, dStopC)

        // XXX TODO -- the variable contents should ideally be looked at
        // during the edit performance

        val oldSpan = vr()
        val newSpan = oldSpan.value match {
          case Span.From (start)  => Span.From (start + dStartCC)
          case Span.Until(stop )  => Span.Until(stop  + dStopCC )
          case Span(start, stop)  =>
            val newStart = start + dStartCC
            Span(newStart, math.max(newStart + 32 /* MinDur */, stop + dStopCC))
          case other => other
        }

        import de.sciss.equal.Implicits._
        val newSpanEx = SpanLikeObj.newConst[T](newSpan)
        if (newSpanEx === oldSpan) None else {
          val name  = "Resize"
          val edit0 = EditVar.Expr[T, SpanLike, SpanLikeObj](name, vr, newSpanEx)
          val edit1Opt: Option[UndoableEdit] = if (dStartCC == 0L) None else obj match {
            case objT: Proc[T] =>
              for {
                audioCue <- ProcActions.getAudioRegion(objT)
              } yield {
                // Crazy heuristics
                audioCue match {
                  case AudioCue.Obj.Shift(peer, amt) =>

                    amt match {
                      case LongObj.Var(amtVr) =>
                        EditVar.Expr[T, Long, LongObj](name, amtVr, amtVr() + dStartCC)
                      case _ =>
                        val newCue = AudioCue.Obj.Shift(peer, LongObj.newVar[T](amt + dStartCC))
                        EditAttrMap(name, objT, Proc.graphAudio, Some(newCue))
                    }
                  case other =>
                    val newCue = AudioCue.Obj.Shift(other, LongObj.newVar[T](dStartCC))
                    EditAttrMap(name, objT, Proc.graphAudio, Some(newCue))
                }
              }
            case _ => None
          }
          CompoundEdit(edit0 :: edit1Opt.toList, name)
        }
      } else None
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
      // in the case of const, just overwrite, in the case of
      // var, check the value stored in the var, and update the var
      // instead (recursion). otherwise, it will be some combinatorial
      // expression, and we could decide to construct a binary op instead!
      // val expr = ExprImplicits[T]

      import de.sciss.equal.Implicits._
      import expr.Ops._
      val newTrackOpt: Option[IntObj[T]] = obj.attr.$[IntObj](ObjTimelineView.attrTrackIndex) match {
        case Some(IntObj.Var(vr)) => Some(vr() + deltaTrack)
        case other =>
          val v = other.fold(0)(_.value) + deltaTrack
          if (v === 0) None else Some(IntObj.newConst(v)) // default is zero; in that case remove attribute
      }
      val edit = EditAttrMap.expr[T, Int, IntObj]("Adjust Track Placement", obj,
        ObjTimelineView.attrTrackIndex, newTrackOpt)

      edits ::= edit
    }

    val name    = "Move"
    val deltaC  = calcSpanDeltaClipped(span, amount, minStart = minStart)
    if (deltaC != 0L) {
      // val imp = ExprImplicits[T]
      span match {
        case SpanLikeObj.Var(vr) =>
          // s.transform(_ shift deltaC)
          import expr.Ops._
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
          import expr.Ops._
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