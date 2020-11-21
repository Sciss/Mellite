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

package de.sciss.mellite.impl.timeline.tool

import de.sciss.desktop.OptionPane
import de.sciss.equal.Implicits._
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj, StringObj}
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.{BasicTool, GUI, ObjView, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.proc.Timeline
import de.sciss.span.Span

import java.awt
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.undo.UndoableEdit
import scala.math.{max, min}
import scala.swing.{FlowPanel, Label, TextField}

final class CursorImpl[T <: Txn[T]](val canvas: TimelineTrackCanvas[T])
  extends CollectionImpl[T, TimelineTool.Cursor] {

  def name                  = "Cursor"
  val icon: Icon            = GUI.iconNormal(Shapes.Pointer) // ToolsImpl.getIcon("text")

  private def renameName = "Rename Region"

  override protected val hover: Boolean = true

  private[this] var dragSel         = false
  private[this] var dragSelPos0     = 0L
  private[this] var dragSelStarted  = false

  override protected def defaultCursor: awt.Cursor =
    awt.Cursor.getPredefinedCursor(awt.Cursor.TEXT_CURSOR)

  private def timeMod = canvas.timelineModel.modifiableOption

  override protected def handleOutside(e: MouseEvent, pos: Long, modelY: Int): Unit =
    timeMod.foreach { mod =>
      mod.selection   = Span.Void
      mod.position    = pos
      dragSel         = true
      dragSelPos0     = pos
      dragSelStarted  = false
    }

  override protected def handleDrag(e: MouseEvent, pos: Long, modelY: Int, childOpt: Option[C]): Unit =
    if (dragSel) timeMod.foreach { mod =>
      mod.selection = if (pos == dragSelPos0) Span.Void else Span(min(pos, dragSelPos0), max(pos, dragSelPos0))
//      mod.position  = pos
      dragSelStarted  = true
    }

  override protected def handleRelease(e: MouseEvent, pos: Long, modelY: Int, childOpt: Option[C]): Unit = {
//    println(s"handleRelease $dragSel $dragSelStarted")
    if (dragSel && dragSelStarted) timeMod.foreach { mod =>
      mod.selection.startOption.foreach { posS =>
        mod.position = posS
      }
    }
  }

  protected def handleSelect(e: MouseEvent, pos: Long, modelY: Int, child: C): Unit = {
    dragSel = false
    if (e.getClickCount === 2) {
      val ggText  = new TextField(child.name, 24)
      val panel   = new FlowPanel(new Label("Name:"), ggText)

      val pane    = OptionPane(panel, OptionPane.Options.OkCancel, OptionPane.Message.Question, focus = Some(ggText))
      pane.title  = renameName
      val res     = pane.show(None) // XXX TODO: search for window source
      if (res === OptionPane.Result.Ok && ggText.text != child.name) {
        val text    = ggText.text
        val nameOpt = if (text === "" || text === ObjView.Unnamed) None else Some(text)
        dispatch(BasicTool.Adjust(TimelineTool.Cursor(nameOpt)))
      }

    } else {
      timeMod.foreach { mod =>
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
  }

  protected def commitObj(drag: TimelineTool.Cursor)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    Some(Edits.setName(obj, drag.name.map(n => StringObj.newConst(n): StringObj[T])))
}