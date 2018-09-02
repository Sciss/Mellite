/*
 *  ViewJ.scala
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

package de.sciss.mellite
package gui
package impl
package audiocue

import java.awt.{Color, Graphics2D}
import javax.swing.JComponent

import de.sciss.audiowidgets.TimelineModel
import de.sciss.audiowidgets.impl.TimelineCanvasImpl
import de.sciss.lucre.swing._
import de.sciss.synth.proc.TimeRef
import de.sciss.{desktop, sonogram}

import scala.swing.Component
import scala.swing.Swing._
import scala.swing.event.MousePressed
import scala.util.{Failure, Success}

final class ViewJ(sono: sonogram.Overview, val timelineModel: TimelineModel)
  extends TimelineCanvasImpl {

  import TimelineCanvasImpl._

  // private val numChannels = sono.inputSpec.numChannels
  // private val minFreq     = sono.config.sonogram.minFreq
  // private val maxFreq     = sono.config.sonogram.maxFreq

  //  private val meters  = Vector.fill(numChannels) {
  //    val res   = new PeakMeterBar(javax.swing.SwingConstants.VERTICAL)
  //    res.ticks = 50
  //    res
  //  }

  def visualBoost: Float = canvasComponent.sonogramBoost
  def visualBoost_=(value: Float): Unit = {
    canvasComponent.sonogramBoost = value
    canvasComponent.repaint()
  }

  object canvasComponent extends Component with sonogram.PaintController {
    private[this] var paintFun: Graphics2D => Unit = paintChecker("Calculating...")
    private[this] val srRatio = sono.inputSpec.sampleRate / TimeRef.SampleRate

    private[ViewJ] var sonogramBoost: Float = 1f

    override def paintComponent(g: Graphics2D): Unit = {
      paintFun(g)
      paintPosAndSelection(g, height)
    }

    @inline def width : Int = peer.getWidth
    @inline def height: Int = peer.getHeight

    preferredSize = {
      val b = desktop.Util.maximumWindowBounds
      (b.width >> 1, b.height >> 1)
    }

    private def paintChecker(message: String)(g: Graphics2D): Unit = {
      g.setPaint(pntChecker)

      g.fillRect(0, 0, width, height)
      g.setColor(Color.white)
      g.drawString(message, 10, 20)
    }

    private def paintReady(g: Graphics2D): Unit = {
      val visSpan   = timelineModel.visible
      val fileStart = visSpan.start * srRatio
      val fileStop  = visSpan.stop  * srRatio
      sono.paint(spanStart = fileStart, spanStop = fileStop, g, 0, 0, width, height, this)
    }

    def adjustGain(amp: Float, pos: Double): Float = amp * sonogramBoost

    def imageObserver: JComponent = peer

    private def ready(): Unit = {
      paintFun = paintReady
      repaint()
    }

    private def failed(exception: Throwable): Unit = {
      val message = s"${exception.getClass.getName} - ${exception.getMessage}"
      paintFun    = paintChecker(message)
      repaint()
    }

    // ---- constructor ----

    sono.onComplete {
      case Success(_) => /* println("SUCCESS"); */ defer(ready())
      case Failure(e) => /* println("FAILURE"); */ defer(failed(e))
    }

    listenTo(mouse.clicks)
    reactions += {
      case _: MousePressed => requestFocus()
    }
  }

  //  private val meterPane   = new BoxPanel(Orientation.Vertical) {
  //    meters.foreach(m => contents += Component.wrap(m))
  //  }
}