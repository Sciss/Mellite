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
import java.awt.event.MouseEvent

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj}
import de.sciss.mellite.TimelineTool.Resize
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.{GUI, ObjTimelineView, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.proc.Timeline
import de.sciss.span.Span
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.math.{abs, min}

final class ResizeImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Resize] {

  type A = TimelineTool.Resize

  override protected val hover: Boolean = true

  def defaultCursor: awt.Cursor = null // awt.Cursor.getPredefinedCursor(awt.Cursor.W_RESIZE_CURSOR)
  val name                  = "Resize"
  val icon: Icon            = GUI.iconNormal(Shapes.Crop)

  private var lastCursor: awt.Cursor = _

  override protected def handleHover(e: MouseEvent, modelY: Int, pos: Long,
                                     childOpt: Option[ObjTimelineView[T]]): Unit = {
    // println(s"handleHover($e, $modelY, $pos, $childOpt")
    val csr = childOpt.fold(null: awt.Cursor) { child =>
      val modelYF = canvas.screenToModelPosF(e.getY)
      val edge    = calcEdge(child, modelYF, pos)
      val csrId = {
        if      (edge.timeStart ) awt.Cursor.W_RESIZE_CURSOR
        else if (edge.timeStop  ) awt.Cursor.E_RESIZE_CURSOR
        else if (edge.trackStart) awt.Cursor.N_RESIZE_CURSOR
        else if (edge.trackStop ) awt.Cursor.S_RESIZE_CURSOR
        else                      awt.Cursor .DEFAULT_CURSOR
      }
      awt.Cursor.getPredefinedCursor(csrId)
      // println(s"CURSOR $csr")
    }

    if (lastCursor != csr) {
      lastCursor = csr
      e.getComponent.setCursor(csr)
    }
  }

  protected def dialog(): Option[Resize] = None // not yet supported

  private final class Edge(val timeStart: Boolean, val timeStop: Boolean,
                           val trackStart: Boolean, val trackStop: Boolean)

  private def calcEdge(region: Initial, modelY: Double /*Int*/, pos: Long): Edge = {
    import region.{trackHeight, trackIndex => trackStart}
    val trackStop   = trackStart + trackHeight
    val insetY1     = abs(modelY - trackStart).toDouble / trackHeight
    val insetY2     = abs(modelY - trackStop ).toDouble / trackHeight
    val insetYMin   = min(insetY1, insetY2)
    region.spanValue match {
      case Span.From (start) =>
        // use time edge if the cursor is in the middle vertical half of the region
        val _useTime          = insetYMin > 0.25
        val _useTrackStart    = !_useTime && insetY1 < insetY2
        val _useTrackStop     = !_useTime && !_useTrackStart
        new Edge(_useTime, false, _useTrackStart, _useTrackStop)
      case Span.Until(stop) =>
        // use time edge if the cursor is in the middle vertical half of the region
        val _useTime          = insetYMin > 0.25
        val _useTrackStart    = !_useTime && insetY1 < insetY2
        val _useTrackStop     = !_useTime && !_useTrackStart
        new Edge(false, _useTime, _useTrackStart, _useTrackStop)
      case Span(start, stop)  =>
        // we looking at an "X" drawn on the rectangle and determine
        // in which of the four sectors the cursor is
        val spanLen           = stop - start
        val insetTime1        = abs(pos - start).toDouble / spanLen
        val insetTime2        = abs(pos - stop ).toDouble / spanLen
        val insetTimeMin      = min(insetTime1, insetTime2)
        val _useTime          = insetTimeMin < insetYMin
        val _useStart         = _useTime && insetTime1 < insetTime2
        val _useStop          = _useTime && !_useStart
        val _useTrackStart    = !_useTime && insetY1 < insetY2
        val _useTrackStop     = !_useTime && !_useTrackStart
        new Edge(_useStart, _useStop, _useTrackStart, _useTrackStop)

      case _ =>
        val _useTrackStart    = insetY1 < insetY2
        val _useTrackStop     = !_useTrackStart
        new Edge(false, false, _useTrackStart, _useTrackStop)
    }
  }

  protected def dragToParam(d: Drag): Resize = {
    val firstModelYF = canvas.screenToModelPosF(d.firstEvent.getY)
    val edge = calcEdge(d.initial, firstModelYF /*d.firstModelY*/, d.firstPos)
    val (dStart, dStop) = if (edge.timeStart) {
      (d.currentPos - d.firstPos, 0L)
    } else if (edge.timeStop) {
      (0L, d.currentPos - d.firstPos)
    } else {
      (0L, 0L)
    }
    val (dTrackStart, dTrackStop) = if (edge.trackStart) {
      (d.currentModelY - d.firstModelY, 0)
    } else if (edge.trackStop) {
      (0, d.currentModelY - d.firstModelY)
    } else {
      (0, 0)
    }
    Resize(dStart, dStop, dTrackStart, dTrackStop)
  }

  protected def commitObj(drag: Resize)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                          (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val minStart = TimelineNavigation.minStart(canvas.timelineModel)
    Edits.resize(span, obj, drag, minStart = minStart)
  }
}
