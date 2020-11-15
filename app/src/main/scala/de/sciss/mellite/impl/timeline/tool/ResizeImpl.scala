/*
 *  ResizeImpl.scala
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

import java.awt

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj}
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.{GUI, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.span.Span
import de.sciss.proc.Timeline
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class ResizeImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Resize] {

  import TimelineTool.Resize

  def defaultCursor: awt.Cursor = awt.Cursor.getPredefinedCursor(awt.Cursor.W_RESIZE_CURSOR)
  val name                  = "Resize"
  val icon: Icon            = GUI.iconNormal(Shapes.Crop) // ToolsImpl.getIcon("hresize")

  protected def dialog(): Option[Resize] = None // not yet supported

  protected def dragToParam(d: Drag): Resize = {
    val (usesStart, usesStop) = d.initial.spanValue match {
      case Span.From (_)      => (true, false)
      case Span.Until(_)      => (false, true)
      case Span(start, stop)  =>
        val s = math.abs(d.firstPos - start) < math.abs(d.firstPos - stop)
        (s, !s)
      case _                  => (false, false)
    }
    val (dStart, dStop) = if (usesStart) {
      (d.currentPos - d.firstPos, 0L)
    } else if (usesStop) {
      (0L, d.currentPos - d.firstPos)
    } else {
      (0L, 0L)
    }
    Resize(dStart, dStop)
  }

  // ProcActions.resize(span, obj, drag, minStart = minStart)

  protected def commitObj(drag: Resize)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                          (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val minStart = TimelineNavigation.minStart(canvas.timelineModel)
    Edits.resize(span, obj, drag, minStart = minStart)
  }
}
