/*
 *  ToolsImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{FadeViewMode, RegionViewMode, TimelineTool, TimelineTools, TimelineTrackCanvas}
import de.sciss.model.Change
import de.sciss.model.impl.ModelImpl

import java.awt.event.MouseEvent
import javax.swing.event.MouseInputAdapter

final class ToolsImpl[T <: Txn[T]](canvas: TimelineTrackCanvas[T])
  extends TimelineTools[T] with ModelImpl[TimelineTools.Update[T]] {

  import TimelineTools._

  private[this] var _currentTool: TimelineTool[T, _] = TimelineTool.cursor(canvas)

  def currentTool: TimelineTool[T, _] = _currentTool
  def currentTool_=(value: TimelineTool[T, _]): Unit =
    if (_currentTool != value) {
      val oldTool   = _currentTool
      _currentTool  = value
      oldTool.uninstall(canvas.canvasComponent)
      value    .install(canvas.canvasComponent, Option(lastMouse))
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

  private[this] var lastMouse: MouseEvent = _

  private[this] val mia: MouseInputAdapter = new MouseInputAdapter {
    override def mouseEntered (e: MouseEvent): Unit = lastMouse = e
    override def mouseMoved   (e: MouseEvent): Unit = lastMouse = e
    override def mouseDragged (e: MouseEvent): Unit = lastMouse = e
    override def mouseExited  (e: MouseEvent): Unit = lastMouse = null
  }

  // constructor
  {
    val cc = canvas.canvasComponent
    cc.peer.addMouseListener      (mia)
    cc.peer.addMouseMotionListener(mia)
    _currentTool.install(cc, Option(lastMouse))
  }
}