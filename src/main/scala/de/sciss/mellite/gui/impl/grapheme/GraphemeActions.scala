/*
 *  GraphemeActions.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package grapheme

import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.Grapheme

/** Implements the actions defined for the grapheme-view. */
trait GraphemeActions[S <: Sys[S]] {
  _: GraphemeView[S] =>

//  object actionDelete extends Action("Delete") {
//    def apply(): Unit = {
//      val editOpt = withSelection { implicit tx =>_ =>
//        graphemeMod.flatMap { _ =>
//            ... // ProcGUIActions.removeProcs(groupMod, views) // XXX TODO - probably should be replaced by Edits.unlinkAndRemove
//        }
//      }
//      editOpt.foreach(undoManager.add)
//    }
//  }
//
//  object actionClearSpan extends Action("Clear Selected Span") {
//    import KeyStrokes._
//    accelerator = Some(menu1 + Key.BackSlash)
//    enabled     = false
//
//    def apply(): Unit =
//      timelineModel.selection.nonEmptyOption.foreach { selSpan =>
//        val editOpt = cursor.step { implicit tx =>
//          graphemeMod.flatMap { groupMod =>
//            editClearSpan(groupMod, selSpan)
//          }
//        }
//        editOpt.foreach(undoManager.add)
//      }
//  }

//  object actionRemoveSpan extends Action("Remove Selected Span") {
//    import KeyStrokes._
//    accelerator = Some(menu1 + shift + Key.BackSlash)
//    enabled = false
//
//    def apply(): Unit = {
//      timelineModel.selection.nonEmptyOption.foreach { selSpan =>
////        val minStart = timelineModel.bounds.start
//        val editOpt = cursor.step { implicit tx =>
//          graphemeMod.flatMap { _ =>
//            // ---- remove ----
//            // - first call 'clear'
//            // - then move everything right of the selection span's stop to the left
//            //   by the selection span's length
////            val editClear = editClearSpan(groupMod, selSpan)
//              ...
////            val affected  = groupMod.intersect(Span.From(selSpan.stop))
////            val amount    = ProcActions.Move(deltaTime = -selSpan.length, deltaTrack = 0, copy = false)
////            val editsMove = affected.flatMap {
////              case (_ /* elemSpan */, elems) =>
////                elems.flatMap { timed =>
////                  Edits.move(timed.span, timed.value, amount, minStart = minStart)
////                }
////            } .toList
////
////            CompoundEdit(editClear.toList ++ editsMove, title)
//          }
//        }
//        editOpt.foreach(undoManager.add)
//        timelineModel.modifiableOption.foreach { tlm =>
//          tlm.selection = Span.Void
//          tlm.position  = selSpan.start
//        }
//      }
//    }
//  }

//  object actionMoveObjectToCursor extends Action("Move Object To Cursor") {
//    enabled = false
//
//    def apply(): Unit = {
////      val pos = timelineModel.position
//        ...
////      val edits = withSelection { implicit tx => views =>
////        val list = views.flatMap { view =>
////          val span = view.span
////          span.value match {
////            case hs: Span.HasStart if hs.start != pos =>
////              val delta   = pos - hs.start
////              val amount  = ProcActions.Move(deltaTime = delta, deltaTrack = 0, copy = false)
////              Edits.move(span, view.obj, amount = amount, minStart = 0L)
////            case _ => None
////          }
////        }
////        if (list.isEmpty) None else Some(list.toList)
////      } .getOrElse(Nil)
////      val editOpt = CompoundEdit(edits, title)
////      editOpt.foreach(undoManager.add)
//    }
//  }

  // -----------

  protected def graphemeMod(implicit tx: S#Tx): Option[Grapheme.Modifiable[S]] =
    grapheme.modifiableOption

//  // ---- clear ----
//  // - find the objects that overlap with the selection span
//  // - if the object is contained in the span, remove it
//  // - if the object overlaps the span, split it once or twice,
//  //   then remove the fragments that are contained in the span
//  protected def editClearSpan(groupMod: proc.Grapheme.Modifiable[S], selSpan: Span)
//                             (implicit tx: S#Tx): Option[UndoableEdit] = {
//      ...
////    val allEdits = groupMod.intersect(selSpan).flatMap {
////      case (elemSpan, elems) =>
////        elems.flatMap { timed =>
////          if (selSpan contains elemSpan) {
////            Edits.unlinkAndRemove(groupMod, timed.span, timed.value) :: Nil
////          } else {
////            timed.span match {
////              case SpanLikeObj.Var(oldSpan) =>
////                val (edits1, span2, obj2) = splitObject(groupMod, selSpan.start, oldSpan, timed.value)
////                val edits3 = if (selSpan contains span2.value) edits1 else {
////                  val (edits2, _, _) = splitObject(groupMod, selSpan.stop, span2, obj2)
////                  edits1 ++ edits2
////                }
////                val edit4 = Edits.unlinkAndRemove(groupMod, span2, obj2)
////                edits3 ++ List(edit4)
////
////              case _ => Nil
////            }
////          }
////        }
////    } .toList
////    CompoundEdit(allEdits, "Clear Span")
//  }

  protected def withSelection[A](fun: S#Tx => TraversableOnce[GraphemeObjView[S]] => Option[A]): Option[A] =
    if (selectionModel.isEmpty) None else {
      val sel = selectionModel.iterator
      cursor.step { implicit tx => fun(tx)(sel) }
    }

  protected def withFilteredSelection[A](p: GraphemeObjView[S] => Boolean)
                                        (fun: S#Tx => TraversableOnce[GraphemeObjView[S]] => Option[A]): Option[A] = {
    val sel = selectionModel.iterator
    val flt = sel.filter(p)
    if (flt.hasNext) cursor.step { implicit tx => fun(tx)(flt) } else None
  }
}