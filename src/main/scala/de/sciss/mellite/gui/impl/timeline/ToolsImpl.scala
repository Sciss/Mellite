/*
 *  ToolsImpl.scala
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

package de.sciss.mellite.gui.impl.timeline

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{FadeViewMode, RegionViewMode, TimelineTool, TimelineTools, TimelineTrackCanvas}
import de.sciss.model.Change
import de.sciss.model.impl.ModelImpl

final class ToolsImpl[S <: Sys[S]](canvas: TimelineTrackCanvas[S])
  extends TimelineTools[S] with ModelImpl[TimelineTools.Update[S]] {

  import TimelineTools._

  private[this] var _currentTool: TimelineTool[S, _] = TimelineTool.cursor(canvas)

  def currentTool: TimelineTool[S, _] = _currentTool
  def currentTool_=(value: TimelineTool[S, _]): Unit =
    if (_currentTool != value) {
      val oldTool   = _currentTool
      _currentTool  = value
      oldTool.uninstall(canvas.canvasComponent)
      value    .install(canvas.canvasComponent)
      dispatch(ToolChanged(Change(oldTool, value)))
    }

  private[this] var _visualBoost: Float = 1f

  def visualBoost: Float = _visualBoost
  def visualBoost_=(value: Float): Unit =
    if (_visualBoost != value) {
      val oldBoost  = _visualBoost
      _visualBoost  = value
      dispatch(VisualBoostChanged(Change(oldBoost, value)))
    }

  private[this] var _fadeViewMode: FadeViewMode = FadeViewMode.Curve

  def fadeViewMode: FadeViewMode = _fadeViewMode
  def fadeViewMode_=(value: FadeViewMode): Unit =
    if (_fadeViewMode != value) {
      val oldMode   = _fadeViewMode
      _fadeViewMode = value
      dispatch(FadeViewModeChanged(Change(oldMode, value)))
    }

  private[this] var _regionViewMode: RegionViewMode = RegionViewMode.TitledBox

  def regionViewMode: RegionViewMode = _regionViewMode
  def regionViewMode_=(value: RegionViewMode): Unit =
    if (_regionViewMode != value) {
      val oldMode     = _regionViewMode
      _regionViewMode = value
      dispatch(RegionViewModeChanged(Change(oldMode, value)))
    }

  _currentTool.install(canvas.canvasComponent)
}