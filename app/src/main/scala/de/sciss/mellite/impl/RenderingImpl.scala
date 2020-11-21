/*
 *  RenderingImpl.scala
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

package de.sciss.mellite.impl

import de.sciss.mellite
import de.sciss.mellite.BasicRendering
import de.sciss.synth.Curve

import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.{BasicStroke, Graphics2D, LinearGradientPaint, Paint, Rectangle, Stroke, TexturePaint, Color => JColor}
import scala.math.{log10, max}

object RenderingImpl {
  private final val pntFadeFill: Paint = {
    val img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB)
    img.setRGB(0, 0, 4, 2, Array(
      0xFF05AF3A, 0x00000000, 0x00000000, 0x00000000,
      0x00000000, 0x00000000, 0xFF05AF3A, 0x00000000
    ), 0, 4)
    new TexturePaint(img, new Rectangle(0, 0, 4, 2))
  }
  private final val pntFadeOutline : Paint = new JColor(0x05, 0xAF, 0x3A)

  private val colrRegionOutline           = new JColor(0x68, 0x68, 0x68)
  private val colrRegionOutlineSelected   = JColor.blue

  private val pntRegionBackground: Paint = new LinearGradientPaint(0f, 1f, 0f, 62f,
    Array[Float](0f, 0.23f, 0.77f, 1f), Array[JColor](new JColor(0x5E, 0x5E, 0x5E), colrRegionOutline,
      colrRegionOutline, new JColor(0x77, 0x77, 0x77)))
  private val pntRegionBackgroundMuted: Paint = new JColor(0xFF, 0xFF, 0xFF, 0x60)
  private val pntRegionBackgroundSelected: Paint = new LinearGradientPaint(0f, 1f, 0f, 62f,
    Array[Float](0f, 0.23f, 0.77f, 1f), Array[JColor](new JColor(0x00, 0x00, 0xE6), colrRegionOutlineSelected,
      colrRegionOutlineSelected, new JColor(0x1A, 0x1A, 0xFF)))

  private[this] val pntBgAquaPixels: Array[Int] = Array(
    0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0,
    0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC, 0xFFECECEC
  )
  private[this] val pntBgDarkPixels: Array[Int] = Array(
    0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F, 0xFF0F0F0F,
    0xFF131313, 0xFF131313, 0xFF131313, 0xFF131313, 0xFF131313, 0xFF131313, 0xFF131313, 0xFF131313
  )

  private val pntBackgroundDark: Paint = {
    val imgDark = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
    imgDark.setRGB(0, 0, 4, 4, pntBgDarkPixels, 0, 4)
    new TexturePaint(imgDark, new Rectangle(0, 0, 4, 4))
  }

  private val pntBackgroundLight: Paint = {
    val imgLight = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
    imgLight.setRGB(0, 0, 4, 4, pntBgAquaPixels, 0, 4)
    new TexturePaint(imgLight, new Rectangle(0, 0, 4, 4))
  }

  private val pntNameShadowDark : Paint   = new JColor(0, 0, 0, 0x80)
  private val pntNameShadowLight: Paint   = new JColor(0xFF, 0xFF, 0xFF, 0x80)
  private val pntNameDark       : Paint   = JColor.white
  private val pntNameLight      : Paint   = JColor.black

  private val pntInlet          : Paint   = JColor.gray
  private val pntInletSpan      : Paint   = JColor.gray
  private val strkInletSpan     : Stroke  = new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f,
    Array[Float](0.5f, 1.5f), 0f)
  private val strkNormal        : Stroke  = new BasicStroke()

}

abstract class RenderingImpl(isDark: Boolean)
  extends BasicRendering {

  import mellite.impl.{RenderingImpl => Impl}

  final val shape1                      : Path2D            = new Path2D.Float()
  final val shape2                      : Path2D            = new Path2D.Float()

  final val pntFadeFill                 : Paint             = Impl.pntFadeFill
  final val pntFadeOutline              : Paint             = Impl.pntFadeOutline

  final val pntBackground               : Paint             = if (isDark) Impl.pntBackgroundDark else Impl.pntBackgroundLight

  final val pntNameShadowDark           : Paint             = Impl.pntNameShadowDark
  final val pntNameShadowLight          : Paint             = Impl.pntNameShadowLight
  final val pntNameDark                 : Paint             = Impl.pntNameDark
  final val pntNameLight                : Paint             = Impl.pntNameLight

  final val pntRegionBackground         : Paint             = Impl.pntRegionBackground
  final val pntRegionBackgroundMuted    : Paint             = Impl.pntRegionBackgroundMuted
  final val pntRegionBackgroundSelected : Paint             = Impl.pntRegionBackgroundSelected

  final val pntRegionOutline            : Paint             = Impl.colrRegionOutline
  final val pntRegionOutlineSelected    : Paint             = Impl.colrRegionOutlineSelected

  final val regionTitleHeight           : Int               = 15
  final val regionTitleBaseline         : Int               = 12

  final val pntInlet                    : Paint             = Impl.pntInlet
  final val pntInletSpan                : Paint             = Impl.pntInletSpan
  final val strokeInletSpan             : Stroke            = Impl.strkInletSpan
  final val strokeNormal                : Stroke            = Impl.strkNormal

  final val clipRect                    : Rectangle         = new Rectangle

  final val intArray1                   : Array[Int]        = new Array(32)
  final val intArray2                   : Array[Int]        = new Array(32)

  // ---- sonogram ----

  final var sonogramBoost: Float = 1.0f

//  final def imageObserver: ImageObserver = component.peer

  final def adjustGain(amp: Float, pos: Double): Float = amp * sonogramBoost

  /** Paints a standardized rendering of a fade.
    *
    * The lowest painted level corresponds to 0.001 or -60 dB.
    *
    * @param g      the target graphics context
    * @param curve  the fade curve
    * @param fw     the fade width in pixels
    * @param pyi    the vertical paint position in pixels
    * @param phi    the paint height in pixels
    * @param y1     the logical start level of the fade
    * @param y2     the logical end level of the fade
    * @param x      the horizontal paint position in pixels
    * @param x0     the horizontal closing position (last line segment) in pixels.
    *               for fade-in, this would be the same as `x`, for fade-out, this
    *               would be x + fw
    */
  def paintFade(g: Graphics2D, curve: Curve, fw: Float, pyi: Int, phi: Int,
                y1: Float, y2: Float, x: Float, x0: Float): Unit = {
    shape1.reset()
    shape2.reset()
    val vScale  = phi / -3f
    val y1s     = max(-3, log10(y1)) * vScale + pyi
    shape1.moveTo(x, y1s)
    shape2.moveTo(x, y1s)
    var xs = 4
    while (xs < fw) {
      val ys = max(-3, log10(curve.levelAt(xs / fw, y1, y2))) * vScale + pyi
      shape1.lineTo(x + xs, ys)
      shape2.lineTo(x + xs, ys)
      xs += 3
    }
    val y2s     = max(-3, log10(y2)) * vScale + pyi
    shape1.lineTo(x + fw, y2s)
    shape2.lineTo(x + fw, y2s)
    shape1.lineTo(x0, pyi)
    g.setPaint(pntFadeFill)
    g.fill    (shape1)
    g.setPaint(pntFadeOutline)
    g.draw    (shape2)
  }
}