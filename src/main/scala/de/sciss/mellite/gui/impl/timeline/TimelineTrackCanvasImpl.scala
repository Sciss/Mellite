/*
 *  TimelineProcCanvasImpl.scala
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

package de.sciss.mellite.gui.impl.timeline

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.BasicTool.DragRubber
import de.sciss.mellite.gui.TimelineTool.EmptyRubber
import de.sciss.mellite.gui.impl.TimelineCanvas2DImpl
import de.sciss.mellite.gui.{TimelineObjView, TimelineTools, TimelineTrackCanvas, TimelineView}

trait TimelineTrackCanvasImpl[S <: Sys[S]]
  extends TimelineCanvas2DImpl[S, Int, TimelineObjView[S]]
    with TimelineTrackCanvas[S] {

  // ---- impl ----

  final val timelineTools: TimelineTools[S] = TimelineTools(this)

  import TimelineTools._

  protected def emptyRubber: DragRubber[Int] = EmptyRubber

  private[this] var _trackIndexOffset = 0

  def trackIndexOffset: Int = _trackIndexOffset
  def trackIndexOffset_=(value: Int): Unit = if (_trackIndexOffset != value) {
    _trackIndexOffset = value
    repaint()
  }

  final def screenToModelPos    (y    : Int): Int     = y / TimelineView.TrackScale + trackIndexOffset
  final def screenToModelExtent (y    : Int): Int     = y / TimelineView.TrackScale

  final def modelPosToScreen    (track  : Int): Double  = (track - trackIndexOffset) * TimelineView.TrackScale
  final def modelExtentToScreen (tracks : Int): Double  = tracks * TimelineView.TrackScale

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