/*
 *  ToolsImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package tracktool

import java.awt.image.BufferedImage
import java.awt.{Color, Font}
import javax.imageio.ImageIO
import javax.swing.ImageIcon

import de.sciss.lucre.synth.Sys
import de.sciss.model.Change
import de.sciss.model.impl.ModelImpl

object ToolsImpl {
  def getImage(name: String): BufferedImage = {
    val is = Mellite.getClass.getResourceAsStream(name)
    val image = if (is != null) {
      val res = ImageIO.read(is)
      is.close()
      res
    } else {
      val res = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
      val g2  = res.createGraphics()
      g2.setColor(Color.black)
      g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18))
      g2.drawString("?", 4, 16)
      g2.dispose()
      res
    }
    image
  }

  def getIcon(name: String): ImageIcon = {
    val image = getImage(s"icon-$name.png")
    new ImageIcon(image)
  }
}
final class ToolsImpl[S <: Sys[S]](canvas: TimelineTrackCanvas[S])
  extends TrackTools[S] with ModelImpl[TrackTools.Update[S]] {

  import TrackTools._

  private var _currentTool: TrackTool[S, _] = TrackTool.cursor(canvas)
  def currentTool: TrackTool[S, _] = _currentTool
  def currentTool_=(value: TrackTool[S, _]): Unit =
    if (_currentTool != value) {
      val oldTool   = _currentTool
      _currentTool  = value
      oldTool.uninstall(canvas.canvasComponent)
      value    .install(canvas.canvasComponent)
      dispatch(ToolChanged(Change(oldTool, value)))
    }

  private var _visualBoost: Float = 1f
  def visualBoost: Float = _visualBoost
  def visualBoost_=(value: Float): Unit =
    if (_visualBoost != value) {
      val oldBoost  = _visualBoost
      _visualBoost  = value
      dispatch(VisualBoostChanged(Change(oldBoost, value)))
    }

  private var _fadeViewMode: FadeViewMode = FadeViewMode.Curve
  def fadeViewMode: FadeViewMode = _fadeViewMode
  def fadeViewMode_=(value: FadeViewMode): Unit =
    if (_fadeViewMode != value) {
      val oldMode   = _fadeViewMode
      _fadeViewMode = value
      dispatch(FadeViewModeChanged(Change(oldMode, value)))
    }

  private var _regionViewMode: RegionViewMode = RegionViewMode.TitledBox
  def regionViewMode: RegionViewMode = _regionViewMode
  def regionViewMode_=(value: RegionViewMode): Unit =
    if (_regionViewMode != value) {
      val oldMode     = _regionViewMode
      _regionViewMode = value
      dispatch(RegionViewModeChanged(Change(oldMode, value)))
    }

  _currentTool.install(canvas.canvasComponent)
}