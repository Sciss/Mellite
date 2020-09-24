/*
 *  TimelineActions.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.desktop.KeyStrokes.menu2
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{KeyStrokes, OptionPane, Window}
import de.sciss.fingertree.RangedSeq
import de.sciss.lucre.bitemp.BiGroup
import de.sciss.lucre.expr.{IntObj, SpanLikeObj, StringObj}
import de.sciss.lucre.stm.{Folder, Obj}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ObjTimelineView, ProcActions, TimelineView}
import de.sciss.mellite.edit.{EditAttrMap, EditTimelineInsertObj, EditTimelineRemoveObj, Edits}
import de.sciss.mellite.impl.TimelineViewBaseImpl
import de.sciss.mellite.impl.proc.ProcObjView
import de.sciss.mellite.impl.proc.ProcGUIActions
import de.sciss.mellite.ActionBounce
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc
import de.sciss.synth.proc.{ObjKeys, Proc, TimeRef, Timeline}
import de.sciss.topology
import de.sciss.topology.Topology
import javax.swing.undo.UndoableEdit

import scala.swing.Action
import scala.swing.event.Key
import scala.util.Success

/** Implements the actions defined for the timeline-view. */
trait TimelineActions[T <: Txn[T]] {
  view: TimelineView[T] with TimelineViewBaseImpl[T, Int, ObjTimelineView[T]] =>

  object actionStopAllSound extends Action("StopAllSound") {
    def apply(): Unit =
      cursor.step { implicit tx =>
        transportView.transport.stop()  // XXX TODO - what else could we do?
        // auralView.stopAll
      }
  }

  object actionBounce extends ActionBounce(this, objH) {

    override protected def prepare(set0: ActionBounce.QuerySettings[T]): ActionBounce.QuerySettings[T] =
      if (timelineModel.selection.isEmpty) set0 else set0.copy(span = timelineModel.selection)

    override protected def spanPresets(): SpanPresets = {
      val all = cursor.step { implicit tx =>
        ActionBounce.presetAllTimeline(obj)
      }
      all ::: timelineModel.selection.nonEmptyOption.map(value => ActionBounce.SpanPreset("Selection", value)).toList
    }
  }

  object actionSelectAll extends Action("Select All") {
    def apply(): Unit = {
      canvas.iterator.foreach { view =>
        if (!selectionModel.contains(view)) selectionModel += view
      }
    }
  }

  object actionSelectFollowing extends Action("Select Following Objects") {
    accelerator = Some(menu2 + Key.F)
    def apply(): Unit = {
      selectionModel.clear()
      val pos = timelineModel.position
      canvas.intersect(Span.from(pos)).foreach { view =>
        // XXX TODO --- this is an additional filter we need because `intersect` doesn't allow >= condition for start
        val ok = view.spanValue match {
          case hs: Span.HasStart => hs.start >= pos
          case _ => false
        }
        if (ok) selectionModel += view
      }
    }
  }

  object actionDelete extends Action("Delete") {
    def apply(): Unit = {
      val editOpt = withSelection { implicit tx => views =>
        timelineMod.flatMap { groupMod =>
          ProcGUIActions.removeProcs(groupMod, views) // XXX TODO - probably should be replaced by Edits.unlinkAndRemove
        }
      }
      editOpt.foreach(undoManager.add)
    }
  }

  object actionSplitObjects extends Action("Split Selected Objects") {
    import KeyStrokes.menu2
    accelerator = Some(menu2 + Key.Y)
    enabled     = false

    def apply(): Unit = {
      val pos     = timelineModel.position
      val pos1    = pos - TimelineView.MinDur
      val pos2    = pos + TimelineView.MinDur
      val editOpt = withFilteredSelection(pv =>
        pv.spanValue.contains(pos1) && pv.spanValue.contains(pos2)) { implicit tx =>

        splitObjects(pos)
      }
      editOpt.foreach(undoManager.add)
    }
  }

  object actionCleanUpObjects extends Action("Vertically Arrange Overlapping Objects") {
    enabled = false

    def apply(): Unit = {
      type V = ObjTimelineView[T]
      case class E(sourceVertex: V, targetVertex: V) extends topology.Edge[V]
      case class Placed(view: ObjTimelineView[T], y: Int) {
        def nextY         : Int     = view.trackHeight + y + 1
        def deltaY        : Int     = y - view.trackIndex
        def isSignificant : Boolean = deltaY != 0
      }

      val edits: List[UndoableEdit] = if (selectionModel.isEmpty) Nil else {
        val sel     = selectionModel.iterator
        val top0    = sel.foldLeft(Topology.empty[V, E])(_ addVertex _)
        val viewSet = top0.vertices.toSet
        cursor.step { implicit tx =>
          val top = top0.vertices.foldLeft(top0) {
            case (topIn, pv: ProcObjView.Timeline[T]) =>
              val targetIt = pv.targets.iterator.map(_.attr.parent).filter(viewSet.contains)
              targetIt.foldLeft(topIn) { case (topIn1, target) =>
                topIn1.addEdge(E(pv, target)) match {
                  case Success((topNew, _)) => topNew
                  case _                    => topIn1
                }
              }

            case (topIn, _) => topIn
          }

          val range0  = RangedSeq.empty[Placed, Long](pl => ObjTimelineView.spanToPoint(pl.view.spanValue), Ordering.Long)
          val pl0     = List.empty[Placed]
          val (_, plRes) = top.vertices.indices.foldLeft((range0, pl0)) { case ((rangeIn, plIn), viewIdx) =>
            val view      = top.vertices(viewIdx)
            val tup       = ObjTimelineView.spanToPoint(view.spanValue)
            val it0       = rangeIn.filterOverlaps(tup).map(_.nextY)
            val it1       = if (viewIdx < top.unconnected || plIn.isEmpty) it0 else it0 ++ Iterator.single(plIn.head.nextY)
            val nextY     = if (it1.isEmpty) 0 else it1.max
            val pl        = Placed(view, nextY)
            val rangeOut  = rangeIn + pl
            val plOut     = pl :: plIn
            (rangeOut, plOut)
          }

          val tl = obj
          plRes.flatMap { pl =>
            if (!pl.isSignificant) None else {
              val move = ProcActions.Move(deltaTime = 0L, deltaTrack = pl.deltaY, copy = false)
              Edits.timelineMoveOrCopy(pl.view.span, pl.view.obj, tl, move, minStart = Long.MinValue)
            }
          }
        }
      }

      val editOpt = CompoundEdit(edits, name = title)
      editOpt.foreach(undoManager.add)
    }
  }

  object actionClearSpan extends Action("Clear Selected Span") {
    import KeyStrokes._
    accelerator = Some(menu1 + Key.BackSlash)
    enabled     = false

    def apply(): Unit =
      timelineModel.selection.nonEmptyOption.foreach { selSpan =>
        val editOpt = cursor.step { implicit tx =>
          timelineMod.flatMap { groupMod =>
            editClearSpan(groupMod, selSpan)
          }
        }
        editOpt.foreach(undoManager.add)
      }
  }

  object actionRemoveSpan extends Action("Remove Selected Span") {
    import KeyStrokes._
    accelerator = Some(menu1 + shift + Key.BackSlash)
    enabled = false

    def apply(): Unit = {
      timelineModel.selection.nonEmptyOption.foreach { selSpan =>
        val minStart = TimelineNavigation.minStart(timelineModel)
        val editOpt = cursor.step { implicit tx =>
          timelineMod.flatMap { groupMod =>
            // ---- remove ----
            // - first call 'clear'
            // - then move everything right of the selection span's stop to the left
            //   by the selection span's length
            val editClear = editClearSpan(groupMod, selSpan)
            val affected  = groupMod.intersect(Span.From(selSpan.stop))
            val amount    = ProcActions.Move(deltaTime = -selSpan.length, deltaTrack = 0, copy = false)
            val editsMove = affected.flatMap {
              case (_ /* elemSpan */, elems) =>
                elems.flatMap { timed =>
                  Edits.timelineMoveOrCopy(timed.span, timed.value, groupMod, amount, minStart = minStart)
                }
            } .toList

            CompoundEdit(editClear.toList ++ editsMove, title)
          }
        }
        editOpt.foreach(undoManager.add)
        timelineModel.modifiableOption.foreach { tlm =>
          tlm.selection = Span.Void
          tlm.position  = selSpan.start
        }
      }
    }
  }

  object actionAlignObjectsToCursor extends Action("Align Objects Start To Cursor") {
    enabled = false

    def apply(): Unit = {
      val pos = timelineModel.position
      val edits = withSelection { implicit tx => views =>
        val tl    = obj
        val list  = views.toIterator.flatMap { _view =>
          val span = _view.span
          span.value match {
            case hs: Span.HasStart if hs.start != pos =>
              val delta   = pos - hs.start
              val amount  = ProcActions.Move(deltaTime = delta, deltaTrack = 0, copy = false)
              Edits.timelineMoveOrCopy(span, _view.obj, tl, amount = amount, minStart = 0L)
            case _ => None
          }
        }
        if (list.isEmpty) None else Some(list.toList)
      } .getOrElse(Nil)
      val editOpt = CompoundEdit(edits, title)
      editOpt.foreach(undoManager.add)
    }
  }

  object actionDropMarker extends Action("Drop Marker") {
    import KeyStrokes._
    accelerator = Some(plain + Key.M)

    final val name        = "Mark"
    final val trackHeight = 2       // marker height

    private def markerSpan(): Span =
      timelineModel.selection match {
        case s: Span    => s
        case Span.Void  =>
          val start = timelineModel.position
          val stop  = start + TimeRef.SampleRate.toLong
          Span(start, stop)
      }

    private def dropTrack(span: Span): Int = {
      val spc = 1     // extra vertical spacing
      val pos = canvas.intersect(span).toList.sortBy(_.trackIndex).foldLeft(0) { (pos0, _view) =>
        val y1 = _view.trackIndex - spc
        val y2 = _view.trackIndex + _view.trackHeight + spc
        if (y1 >= (pos0 + trackHeight) || y2 <= pos0) pos0 else y2
      }
      pos
    }

    def locate(): (Span, Int) = {
      val span    = markerSpan()
      val trkIdx  = dropTrack(span)
      (span, trkIdx)
    }

    def apply(): Unit =
      perform(locate(), name = name)

    def perform(location: (Span, Int), name: String): Unit = {
      val editOpt = cursor.step { implicit tx =>
        timelineMod.map { tlMod =>
          val (span, trkIdx) = location
          val spanObj = SpanLikeObj.newVar[T](span)
          val elem: Obj[T] = IntObj.newConst[T](0)  // XXX TODO --- we should add a 'generic' Obj?
          val attr    = elem.attr
          attr.put(ObjTimelineView.attrTrackIndex , IntObj   .newVar[T](trkIdx      ))
          attr.put(ObjTimelineView.attrTrackHeight, IntObj   .newVar[T](trackHeight ))
          attr.put(ObjKeys.attrName               , StringObj.newVar[T](name        ))
          EditTimelineInsertObj(name = "Drop Marker", timeline = tlMod, span = spanObj, elem = elem)
        }
      }
      editOpt.foreach(undoManager.add)
    }
  }

  object actionDropNamedMarker extends Action("Drop Named Marker") {
    import KeyStrokes._

    accelerator = Some(shift + Key.M)

    def apply(): Unit = {
      val loc = actionDropMarker.locate()
      val opt = OptionPane.textInput("Name:", initial = actionDropMarker.name)
      opt.title = title
      Window.showDialog(component, opt).foreach { name =>
        actionDropMarker.perform(loc, name)
      }
    }
  }

  // -----------

  protected def timelineMod(implicit tx: T): Option[Timeline.Modifiable[T]] =
    obj.modifiableOption

  // ---- clear ----
  // - find the objects that overlap with the selection span
  // - if the object is contained in the span, remove it
  // - if the object overlaps the span, split it once or twice,
  //   then remove the fragments that are contained in the span
  protected def editClearSpan(groupMod: proc.Timeline.Modifiable[T], selSpan: Span)
                             (implicit tx: T): Option[UndoableEdit] = {
    val allEdits = groupMod.intersect(selSpan).flatMap {
      case (elemSpan, elems) =>
        elems.flatMap { timed =>
          if (selSpan contains elemSpan) {
            Edits.unlinkAndRemove(groupMod, timed.span, timed.value) :: Nil
          } else {
            timed.span match {
              case SpanLikeObj.Var(oldSpan) =>
                val (edits1, span2, obj2) = splitObjectImpl(groupMod, oldSpan , timed.value , selSpan.start )
                val edits3 = if (selSpan contains span2.value) edits1 else {
                  val (edits2, _, _)      = splitObjectImpl(groupMod, span2   , obj2        , selSpan.stop  )
                  edits1 ++ edits2
                }
                val edit4 = Edits.unlinkAndRemove(groupMod, span2, obj2)
                edits3 ++ List(edit4)

              case _ => Nil
            }
          }
        }
    } .toList
    CompoundEdit(allEdits, "Clear Span")
  }

  protected def splitObjects(time: Long)(views: TraversableOnce[ObjTimelineView[T]])
                  (implicit tx: T): Option[UndoableEdit] = timelineMod.flatMap { groupMod =>
    val edits: List[UndoableEdit] = views.toIterator.flatMap { pv =>
      pv.span match {
        case SpanLikeObj.Var(oldSpan) =>
          val (edits, _, _) = splitObjectImpl(groupMod, oldSpan, pv.obj, time)
          edits
        case _ => Nil
      }
    } .toList

    CompoundEdit(edits, "Split Objects")
  }

  @deprecated("Should migrate to SP EditTimeline", since = "2.36.0")
  protected def splitObject(tlMod: proc.Timeline.Modifiable[T], span: SpanLikeObj[T], obj: Obj[T], time: Long)
                           (implicit tx: T): (Option[UndoableEdit], SpanLikeObj.Var[T], Obj[T]) = {
    val tup = splitObjectImpl(tlMod, span, obj, time)
    val opt = CompoundEdit(tup._1, "Split Objects")
    (opt, tup._2, tup._3)
  }

  @deprecated("Should migrate to SP EditTimeline", since = "2.36.0")
  private def splitObjectImpl(tlMod: proc.Timeline.Modifiable[T], span: SpanLikeObj[T], obj: Obj[T], time: Long)
                             (implicit tx: T): (List[UndoableEdit], SpanLikeObj.Var[T], Obj[T]) = {
    val leftObj   = obj
    val rightObj  = ProcActions.copy[T](leftObj, connectInput = true)
    rightObj.attr.remove(ObjKeys.attrFadeIn)

    val oldVal    = span.value
    val rightSpan: SpanLikeObj.Var[T] = oldVal match {
      case Span.HasStart(leftStart) =>
        val _rightSpan  = SpanLikeObj.newVar[T](oldVal)
        val resize      = ProcActions.Resize(time - leftStart, 0L)
        val minStart    = TimelineNavigation.minStart(timelineModel)
        ProcActions.resize(_rightSpan, rightObj, resize, minStart = minStart)
        _rightSpan

      case _ =>
        val rightSpanV = oldVal.intersect(Span.from(time))
        SpanLikeObj.newVar[T](rightSpanV)
    }

    var edits = List.empty[UndoableEdit]

    edits ::= EditAttrMap("Remove Fade Out", leftObj, ObjKeys.attrFadeOut, None)

    span match {
      case SpanLikeObj.Var(spanVr) =>
        oldVal match {
          case Span.HasStop(rightStop) =>
            val minStart  = TimelineNavigation.minStart(timelineModel)
            val resize    = ProcActions.Resize(0L, time - rightStop)
            val editOpt   = Edits.resize(spanVr, leftObj, resize, minStart = minStart)
            editOpt.foreach(edits ::= _)

          case Span.HasStart(leftStart) =>
            val leftSpanV = Span(leftStart, time)
            edits ::= EditVar.Expr[T, SpanLike, SpanLikeObj]("Resize", spanVr, leftSpanV)

          case _ =>
        }

      case _ =>
        edits ::= EditTimelineRemoveObj("Split Region", tlMod, span, obj)
        val leftSpanV = oldVal.intersect(Span.until(time))
        val leftSpan  = SpanLikeObj.newVar[T](leftSpanV)
        edits ::= EditTimelineInsertObj("Split Region", tlMod, leftSpan, leftObj)
    }

    edits ::= EditTimelineInsertObj("Split Region", tlMod, rightSpan, rightObj)

    // now try to find targets (tricky! we only scan global procs and their main inputs)
    (leftObj, rightObj) match {
      case (pLeft: Proc[T], pRight: Proc[T]) =>
        (pLeft.outputs.get(Proc.mainOut), pRight.outputs.get(Proc.mainOut)) match {
          case (Some(outLeft), Some(outRight)) =>
            val (it, _) = tlMod.eventsAt(BiGroup.MinCoordinate)
            it.foreach {
              case (Span.All, vec) =>
                vec.foreach { entry =>
                  entry.value match {
                    case sink: Proc[T] =>
                      val hasLink = sink.attr.get(Proc.mainIn).exists {
                        case `outLeft` => true
                        case outF: Folder[T] =>
                          outF.iterator.contains(outLeft)
                        case _        => false
                      }
                      if (hasLink) {
                        edits ::= Edits.addLink(outRight, sink)
                      }

                    case _ =>
                  }
                }
              case _ =>
            }

          case _ =>
        }

      case _ =>
    }

    // debugCheckConsistency(s"Split left = $leftObj, oldSpan = $oldVal; right = $rightObj, rightSpan = ${rightSpan.value}")
    (edits.reverse, rightSpan, rightObj)
  }
}