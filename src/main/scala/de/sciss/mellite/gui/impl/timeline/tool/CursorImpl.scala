/*
 *  CursorImpl.scala
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

package de.sciss.mellite.gui.impl.timeline.tool

import java.awt.Cursor
import java.awt.event.MouseEvent

import de.sciss.desktop.OptionPane
import de.sciss.equal.Implicits._
import de.sciss.lucre.expr.{SpanLikeObj, StringObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.Edits
import de.sciss.mellite.gui.{BasicTool, GUI, ObjView, Shapes, ObjTimelineView, TimelineTool, TimelineTrackCanvas}
import de.sciss.span.Span
import de.sciss.synth.proc.Timeline
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.{FlowPanel, Label, TextField}

final class CursorImpl[S <: Sys[S]](val canvas: TimelineTrackCanvas[S])
  extends CollectionImpl[S, TimelineTool.Cursor] {

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
  def name                  = "Cursor"
  val icon: Icon            = GUI.iconNormal(Shapes.Pointer) // ToolsImpl.getIcon("text")

  private def renameName = "Rename Region"

  protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: ObjTimelineView[S]): Unit =
    if (e.getClickCount === 2) {
      val ggText  = new TextField(region.name, 24)
      val panel   = new FlowPanel(new Label("Name:"), ggText)

      val pane    = OptionPane(panel, OptionPane.Options.OkCancel, OptionPane.Message.Question, focus = Some(ggText))
      pane.title  = renameName
      val res     = pane.show(None) // XXX TODO: search for window source
      if (res === OptionPane.Result.Ok && ggText.text != region.name) {
        val text    = ggText.text
        val nameOpt = if (text === "" || text === ObjView.Unnamed) None else Some(text)
        dispatch(BasicTool.Adjust(TimelineTool.Cursor(nameOpt)))
      }

    } else {
      canvas.timelineModel.modifiableOption.foreach { mod =>
        val it    = canvas.selectionModel.iterator
        val empty = Span.Void: Span.SpanOrVoid
        val all   = it.foldLeft(empty) { (res, pv) =>
          pv.spanValue match {
            case sp @ Span(_, _) => res.nonEmptyOption.fold(sp)(_ union sp)
            case _ => res
          }
        }
        mod.selection = all
      }
    }

  protected def commitObj(drag: TimelineTool.Cursor)(span: SpanLikeObj[S], obj: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] =
    Some(Edits.setName(obj, drag.name.map(n => StringObj.newConst(n): StringObj[S])))
}