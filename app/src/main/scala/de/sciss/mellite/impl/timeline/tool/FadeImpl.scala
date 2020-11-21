/*
 *  FadeImpl.scala
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

import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj}
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.{GUI, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.proc.Timeline
import de.sciss.span.Span

import java.awt
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class FadeImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Fade] {

  import TimelineTool.Fade

  def defaultCursor: awt.Cursor = awt.Cursor.getPredefinedCursor(awt.Cursor.NW_RESIZE_CURSOR)
  val name                  = "Fade"
  val icon: Icon            = GUI.iconNormal(Shapes.Aperture) // ToolsImpl.getIcon("fade")

  private var dragCurve = false

  protected def dragToParam(d: Drag): Fade = {
    val firstSpan = d.initial.spanValue
    val leftHand = firstSpan match {
      case Span(start, stop)  => math.abs(d.firstPos - start) < math.abs(d.firstPos - stop)
      case Span.From (_)      => true
      case Span.Until(_)      => false
      case _                  => true
    }
    val (deltaTime, deltaCurve) = if (dragCurve) {
      val dc = (d.firstEvent.getY - d.currentEvent.getY) * 0.1f
      (0L, if (leftHand) -dc else dc)
    } else {
      (if (leftHand) d.currentPos - d.firstPos else d.firstPos - d.currentPos, 0f)
    }
    if (leftHand) Fade(deltaTime, 0L, deltaCurve, 0f)
    else Fade(0L, deltaTime, 0f, deltaCurve)
  }

  override protected def dragStarted(d: this.Drag): Boolean = {
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
