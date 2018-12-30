/*
 *  ResizeImpl.scala
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

package de.sciss.mellite.gui.impl.timeline.tool

import java.awt.Cursor

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.Edits
import de.sciss.mellite.gui.{GUI, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.span.Span
import de.sciss.synth.proc.Timeline
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class ResizeImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S])
  extends BasicCollection[S, TimelineTool.Resize] {

  import TimelineTool.Resize

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
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

  protected def commitObj(drag: Resize)(span: SpanLikeObj[S], obj: Obj[S], timeline: Timeline[S])
                          (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    val minStart = TimelineNavigation.minStart(canvas.timelineModel)
    Edits.resize(span, obj, drag, minStart = minStart)
  }
}
