/*
 *  FadeImpl.scala
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

package de.sciss.mellite.impl.timeline.tool

import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj}
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.{GUI, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.proc.Timeline
import de.sciss.span.Span

import java.awt
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class FadeImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Fade] {

  import TimelineTool.Fade

  val name                  = "Fade"
  val icon: Icon            = GUI.iconNormal(Shapes.Aperture) // ToolsImpl.getIcon("fade")

  private var dragCurve = false

  override protected val hover: Boolean = true

  override protected def getCursor(e: MouseEvent, modelY: Int, pos: Long,
                                   childOpt: Option[C]): awt.Cursor = childOpt match {
    case Some(c) =>
      awt.Cursor.getPredefinedCursor(awt.Cursor.TEXT_CURSOR)
      val isFadeIn = determineSide(c, pos)
      val cursorId = if (isFadeIn) awt.Cursor.NW_RESIZE_CURSOR else awt.Cursor.NE_RESIZE_CURSOR
      awt.Cursor.getPredefinedCursor(cursorId)
    case None => null
  }

  private def determineSide(c: C, pos: Long): Boolean =
    c.spanValue match {
      case Span(start, stop)  => math.abs(pos - start) < math.abs(pos - stop)
      case Span.From (_)      => true
      case Span.Until(_)      => false
      case _                  => true
    }

  protected def dragToParam(d: Drag): Fade = {
    val isFadeIn  = determineSide(d.initial, d.firstPos)
    val (deltaTime, deltaCurve) = if (dragCurve) {
      val dc = (d.firstEvent.getY - d.currentEvent.getY) * 0.1f
      (0L, if (isFadeIn) -dc else dc)
    } else {
      (if (isFadeIn) d.currentPos - d.firstPos else d.firstPos - d.currentPos, 0f)
    }
    if (isFadeIn) Fade(deltaTime, 0L, deltaCurve, 0f)
    else Fade(0L, deltaTime, 0f, deltaCurve)
  }

  override protected def dragStarted(d: Drag): Boolean = {
    val result = super.dragStarted(d)
    if (result) {
      dragCurve = math.abs(d.currentEvent.getX - d.firstEvent.getX) <
        math.abs(d.currentEvent.getY - d.firstEvent.getY)
    }
    result
  }

  protected def commitObj(drag: Fade)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    Edits.fade(span, obj, drag)

  protected def dialog(): Option[TimelineTool.Fade] = None // XXX TODO
}
