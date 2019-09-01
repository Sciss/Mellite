/*
 *  GraphemeActions.scala
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

package de.sciss.mellite.impl.grapheme

import de.sciss.desktop.KeyStrokes.menu2
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{GraphemeView, ObjGraphemeView}
import de.sciss.mellite.edit.EditGraphemeRemoveObj
import de.sciss.mellite.impl.TimelineViewBaseImpl
import de.sciss.span.Span
import de.sciss.synth.proc.Grapheme

import scala.swing.Action
import scala.swing.event.Key

/** Implements the actions defined for the grapheme-view. */
trait GraphemeActions[S <: Sys[S]] {
  view: GraphemeView[S] with TimelineViewBaseImpl[S, Double, ObjGraphemeView[S]] =>

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
        selectionModel += view
      }
    }
  }

  object actionDelete extends Action("Delete") {
    def apply(): Unit = {
      val editOpt = withSelection { implicit tx => views =>
        graphemeMod.flatMap { grMod =>
          val name = title
          val edits = views.map { view =>
            EditGraphemeRemoveObj(name = name, grapheme = grMod, time = view.time, elem = view.obj)
          } .toList
          CompoundEdit(edits, name)
        }
      }
      editOpt.foreach(undoManager.add)
    }
  }

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
}