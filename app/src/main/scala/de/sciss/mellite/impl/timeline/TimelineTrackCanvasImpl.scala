/*
 *  TimelineProcCanvasImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.lucre.synth.Txn
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.mellite.TimelineTool.EmptyRubber
import de.sciss.mellite.impl.TimelineCanvas2DImpl
import de.sciss.mellite.{ObjTimelineView, TimelineTools, TimelineTrackCanvas, TimelineView}

trait TimelineTrackCanvasImpl[T <: Txn[T]]
  extends TimelineCanvas2DImpl[T, Int, ObjTimelineView[T]]
    with TimelineTrackCanvas[T] {

  // ---- impl ----

  final val timelineTools: TimelineTools[T] = TimelineTools(this)

  import TimelineTools._

  protected def emptyRubber: DragRubber[Int] = EmptyRubber

  private[this] var _trackIndexOffset = 0

  def trackIndexOffset: Int = _trackIndexOffset
  def trackIndexOffset_=(value: Int): Unit = if (_trackIndexOffset != value) {
    _trackIndexOffset = value
    repaint()
  }

  final def screenToModelPos              (y      : Int): Int     = y / TimelineView.TrackScale + trackIndexOffset
  final def screenToModelExtent           (y      : Int): Int     = y / TimelineView.TrackScale

  final override def screenToModelPosF    (y      : Int): Double  = y.toDouble / TimelineView.TrackScale + trackIndexOffset
  final override def screenToModelExtentF (y      : Int): Double  = y.toDouble / TimelineView.TrackScale

  final def modelPosToScreen              (track  : Int): Double  = (track - trackIndexOffset) * TimelineView.TrackScale
  final def modelExtentToScreen           (tracks : Int): Double  = tracks * TimelineView.TrackScale

  final def modelYBox(a: Int, b: Int): (Int, Int) = if (a < b) (a, b - a + 1) else (b, a - b + 1)

  timelineTools.addListener {
    case ToolChanged(change) =>
      change.before.removeListener(toolListener)
      change.now   .addListener   (toolListener)
    case VisualBoostChanged   (_) => repaint()
    case FadeViewModeChanged  (_) => repaint()
    case RegionViewModeChanged(_) => repaint()
  }
  timelineTools.currentTool.addListener(toolListener)
}