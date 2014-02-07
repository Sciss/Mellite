/*
 *  ResizeImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package tracktool

import java.awt.Cursor
import de.sciss.synth.proc.Proc
import de.sciss.span.{SpanLike, Span}
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.synth.Sys

final class ResizeImpl[S <: Sys[S]](protected val canvas: TimelineProcCanvas[S])
  extends BasicRegion[S, TrackTool.Resize] {

  import TrackTool.{Cursor => _, _}

  def defaultCursor = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
  val name          = "Resize"
  val icon          = ToolsImpl.getIcon("hresize")

  protected def dialog(): Option[Resize] = None // not yet supported

  protected def dragToParam(d: Drag): Resize = {
    val (usesStart, usesStop) = d.initial.span match {
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

  protected def commitProc(drag: Resize)(span: Expr[S, SpanLike], proc: Proc[S])(implicit tx: S#Tx): Unit =
    ProcActions.resize(span, proc, drag, canvas.timelineModel)
}
