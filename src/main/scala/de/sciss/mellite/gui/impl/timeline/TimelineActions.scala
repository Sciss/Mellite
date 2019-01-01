/*
 *  TimelineActions.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl.timeline

import de.sciss.audiowidgets.impl.TimelineNavigation
import javax.swing.undo.UndoableEdit
import de.sciss.desktop.KeyStrokes.menu2
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{KeyStrokes, OptionPane, Window}
import de.sciss.fingertree.RangedSeq
import de.sciss.lucre.expr.{IntObj, SpanLikeObj, StringObj}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ProcActions.Move
import de.sciss.mellite.gui.edit.{EditAttrMap, EditTimelineInsertObj, Edits}
import de.sciss.mellite.gui.impl.proc.{ProcGUIActions, ProcObjView}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc
import de.sciss.synth.proc.{ObjKeys, TimeRef, Timeline}
import de.sciss.topology
import de.sciss.topology.Topology

import scala.swing.Action
import scala.swing.event.Key
import scala.util.Success

/** Implements the actions defined for the timeline-view. */
trait TimelineActions[S <: Sys[S]] {
  view: TimelineView[S] =>

  object actionStopAllSound extends Action("StopAllSound") {
    def apply(): Unit =
      cursor.step { implicit tx =>
        transportView.transport.stop()  // XXX TODO - what else could we do?
        // auralView.stopAll
      }
  }

  object actionBounce extends ActionBounceTimeline(this, timelineH) {
    import ActionBounceTimeline._

    override protected def prepare(set0: QuerySettings[S]): QuerySettings[S] =
      if (timelineModel.selection.isEmpty) set0 else set0.copy(span = timelineModel.selection)

    override protected def spanPresets(): SpanPresets = {
      val all = cursor.step { implicit tx =>
        presetAllTimeline(timeline)
      }
      all ::: timelineModel.selection.nonEmptyOption.map(value => SpanPreset("Selection", value)).toList
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
      val editOpt = withFilteredSelection(pv => pv.spanValue.contains(pos1) && pv.spanValue.contains(pos2)) { implicit tx =>
        splitObjects(pos)
      }
      editOpt.foreach(undoManager.add)
    }
  }

  object actionCleanUpObjects extends Action("Vertically Arrange Overlapping Objects") {
    enabled = false

    def apply(): Unit = {
      type V = TimelineObjView[S]
      case class E(sourceVertex: V, targetVertex: V) extends topology.Edge[V]
      case class Placed(view: TimelineObjView[S], y: Int) {
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
            case (topIn, pv: ProcObjView.Timeline[S]) =>
              val targetIt = pv.targets.iterator.map(_.attr.parent).filter(viewSet.contains)
              targetIt.foldLeft(topIn) { case (topIn1, target) =>
                topIn1.addEdge(E(pv, target)) match {
                  case Success((topNew, _)) => topNew
                  case _                    => topIn1
                }
              }

            case (topIn, _) => topIn
          }

          val range0  = RangedSeq.empty[Placed, Long](pl => TimelineObjView.spanToPoint(pl.view.spanValue), Ordering.Long)
          val pl0     = List.empty[Placed]
          val (_, plRes) = top.vertices.indices.foldLeft((range0, pl0)) { case ((rangeIn, plIn), viewIdx) =>
            val view      = top.vertices(viewIdx)
            val tup       = TimelineObjView.spanToPoint(view.spanValue)
            val it0       = rangeIn.filterOverlaps(tup).map(_.nextY)
            val it1       = if (viewIdx < top.unconnected || plIn.isEmpty) it0 else it0 ++ Iterator.single(plIn.head.nextY)
            val nextY     = if (it1.isEmpty) 0 else it1.max
            val pl        = Placed(view, nextY)
            val rangeOut  = rangeIn + pl
            val plOut     = pl :: plIn
            (rangeOut, plOut)
          }

          val tl = timeline
          plRes.flatMap { pl =>
            if (!pl.isSignificant) None else {
              val move = Move(deltaTime = 0L, deltaTrack = pl.deltaY, copy = false)
              Edits.timelineMoveOrCopy(pl.view.span, pl.view.obj, tl, move, minStart = Long.MinValue)
            }
          }
        }
      }

//            def checkLink(source: TimelineObjView[S], target: TimelineObjView[S]): Unit = {
//              val targets = getTargets(source)
//              if (targets.contains(target)) {
//                val edge = E(source, target)
//                top.addEdge(edge).foreach { case (top1, moveOpt) =>
//                  top = top1
//                  moveOpt.foreach { move =>
//                    // - In case that the reference is the source vertex of the added edge, the affected vertices
//                    //   should be moved _after_ the reference and keep their internal grouping order.
//                    // - In case the reference is the target vertex, the affected vertices should be
//                    //   moved _before_ the reference
//                    if (move.isAfter) {
//                      val thisPl = placed.getOrElseUpdate(view, Placed(trkIdx = view.trackIndex, trkH = view.trackHeight))
//                      move.affected.foreach {}
//                      ???
//                    } else {
//                      ???
//                    }
//                  }
//                }
//              }
//            }
//
//            checkLink(view, that)
//            checkLink(that, view)

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
        val tl    = timeline
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
          val spanObj = SpanLikeObj.newVar[S](span)
          val elem: Obj[S] = IntObj.newConst[S](0)  // XXX TODO --- we should add a 'generic' Obj?
          val attr    = elem.attr
          attr.put(TimelineObjView.attrTrackIndex , IntObj   .newVar[S](trkIdx      ))
          attr.put(TimelineObjView.attrTrackHeight, IntObj   .newVar[S](trackHeight ))
          attr.put(ObjKeys.attrName               , StringObj.newVar[S](name        ))
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

  protected def timelineMod(implicit tx: S#Tx): Option[Timeline.Modifiable[S]] =
    timeline.modifiableOption

  // ---- clear ----
  // - find the objects that overlap with the selection span
  // - if the object is contained in the span, remove it
  // - if the object overlaps the span, split it once or twice,
  //   then remove the fragments that are contained in the span
  protected def editClearSpan(groupMod: proc.Timeline.Modifiable[S], selSpan: Span)
                             (implicit tx: S#Tx): Option[UndoableEdit] = {
    val allEdits = groupMod.intersect(selSpan).flatMap {
      case (elemSpan, elems) =>
        elems.flatMap { timed =>
          if (selSpan contains elemSpan) {
            Edits.unlinkAndRemove(groupMod, timed.span, timed.value) :: Nil
          } else {
            timed.span match {
              case SpanLikeObj.Var(oldSpan) =>
                val (edits1, span2, obj2) = splitObject(groupMod, selSpan.start, oldSpan, timed.value)
                val edits3 = if (selSpan contains span2.value) edits1 else {
                  val (edits2, _, _) = splitObject(groupMod, selSpan.stop, span2, obj2)
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

  protected def splitObjects(time: Long)(views: TraversableOnce[TimelineObjView[S]])
                  (implicit tx: S#Tx): Option[UndoableEdit] = timelineMod.flatMap { groupMod =>
    val edits: List[UndoableEdit] = views.toIterator.flatMap { pv =>
      pv.span match {
        case SpanLikeObj.Var(oldSpan) =>
          val (edits, _, _) = splitObject(groupMod, time, oldSpan, pv.obj)
          edits
        case _ => Nil
      }
    } .toList

    CompoundEdit(edits, "Split Objects")
  }

  private def splitObject(groupMod: proc.Timeline.Modifiable[S], time: Long, oldSpan: SpanLikeObj.Var[S],
                          obj: Obj[S])(implicit tx: S#Tx): (List[UndoableEdit], SpanLikeObj.Var[S], Obj[S]) = {
    // val imp = ExprImplicits[S]
    val leftObj   = obj // pv.obj()
    val rightObj  = ProcActions.copy[S](leftObj, connectInput = true)
    rightObj.attr.remove(ObjKeys.attrFadeIn)

    val oldVal    = oldSpan.value
    val rightSpan = oldVal match {
      case Span.HasStart(leftStart) =>
        val _rightSpan  = SpanLikeObj.newVar(oldSpan())
        val resize      = ProcActions.Resize(time - leftStart, 0L)
        val minStart    = TimelineNavigation.minStart(timelineModel)
        // println("----BEFORE RIGHT----")
        // debugPrintAudioGrapheme(rightObj)
        ProcActions.resize(_rightSpan, rightObj, resize, minStart = minStart)
        // println("----AFTER RIGHT ----")
        // debugPrintAudioGrapheme(rightObj)
        _rightSpan

      case Span.HasStop(rightStop) =>
        SpanLikeObj.newVar[S](Span(time, rightStop))
    }

    val editRemoveFadeOut = EditAttrMap("Remove Fade Out", leftObj, ObjKeys.attrFadeOut, None)

    val editLeftSpan: Option[UndoableEdit] = oldVal match {
      case Span.HasStop(rightStop) =>
        val minStart  = TimelineNavigation.minStart(timelineModel)
        val resize    = ProcActions.Resize(0L, time - rightStop)
        Edits.resize(oldSpan, leftObj, resize, minStart = minStart)

      case Span.HasStart(leftStart) =>
        val leftSpan = Span(leftStart, time)
        val edit = EditVar.Expr[S, SpanLike, SpanLikeObj]("Resize", oldSpan, leftSpan)
        Some(edit)
    }

    // group.add(rightSpan, rightObj)
    val editAdd = EditTimelineInsertObj("Split Region", groupMod, rightSpan, rightObj)

    // debugCheckConsistency(s"Split left = $leftObj, oldSpan = $oldVal; right = $rightObj, rightSpan = ${rightSpan.value}")
    val list1 = editAdd :: Nil
    val list2 = editLeftSpan.fold(list1)(_ :: list1)
    val list3 = editRemoveFadeOut :: list2
    (list3, rightSpan, rightObj)
  }

  protected def withSelection[A](fun: S#Tx => TraversableOnce[TimelineObjView[S]] => Option[A]): Option[A] =
    if (selectionModel.isEmpty) None else {
      val sel = selectionModel.iterator
      cursor.step { implicit tx => fun(tx)(sel) }
    }

  protected def withFilteredSelection[A](p: TimelineObjView[S] => Boolean)
                                      (fun: S#Tx => TraversableOnce[TimelineObjView[S]] => Option[A]): Option[A] = {
    val sel = selectionModel.iterator
    val flt = sel.filter(p)
    if (flt.hasNext) cursor.step { implicit tx => fun(tx)(flt) } else None
  }
}