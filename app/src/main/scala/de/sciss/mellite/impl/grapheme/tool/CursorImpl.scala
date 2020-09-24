/*
 *  CursorImpl.scala
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

package de.sciss.mellite.impl.grapheme.tool

import java.awt.Cursor
import java.awt.event.MouseEvent

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{GUI, GraphemeCanvas, ObjGraphemeView}
import de.sciss.mellite.Shapes
import de.sciss.span.Span
import de.sciss.synth.proc.Grapheme
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class CursorImpl[T <: Txn[T]](val canvas: GraphemeCanvas[T])
  extends CollectionImpl[T, Unit] {

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
  def name                  = "Cursor"
  val icon: Icon            = GUI.iconNormal(Shapes.Pointer) // ToolsImpl.getIcon("text")

  protected def handleSelect(e: MouseEvent, modelY: Double, pos: Long, child: ObjGraphemeView[T]): Unit =
//    if (e.getClickCount == 2) {
//      val ggText  = new TextField(region.name, 24)
//      val panel   = new FlowPanel(new Label("Name:"), ggText)
//
//      val pane    = OptionPane(panel, OptionPane.Options.OkCancel, OptionPane.Message.Question, focus = Some(ggText))
//      pane.title  = renameName
//      val res     = pane.show(None) // XXX TODO: search for window source
//      if (res == OptionPane.Result.Ok && ggText.text != region.name) {
//        val text    = ggText.text
//        val nameOpt = if (text == "" || text == GraphemeObjView.Unnamed) None else Some(text)
//        dispatch(GraphemeTool.Adjust(GraphemeTool.Cursor(nameOpt)))
//      }
//
//    } else
    {
      canvas.timelineModel.modifiableOption.foreach { mod =>
        val it    = canvas.selectionModel.iterator
        val empty = Span.Void: Span.SpanOrVoid
        val all   = it.foldLeft(empty) { (res, _ /*pv */) =>
          res // XXX TODO
//          pv.spanValue match {
//            case sp @ Span(_, _) => res.nonEmptyOption.fold(sp)(_ union sp)
//            case _ => res
//          }
        }
        mod.selection = all
      }
    }

  protected def commitObj(drag: Unit)(time: LongObj[T], child: Obj[T], parent: Grapheme[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = None
}