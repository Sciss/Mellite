/*
 *  Shapes.scala
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

package de.sciss.mellite

import java.awt.geom.{AffineTransform, GeneralPath, Path2D}

import de.sciss.icons.raphael

// XXX TODO --- this is not sustainable; we should just parse SVG strings
object Shapes {
  def plus(fun: Path2D => Unit)(p: Path2D): Unit = {
    val pp = new GeneralPath(Path2D.WIND_EVEN_ODD)
    raphael.Shapes.Plus(pp)
    val at = AffineTransform.getScaleInstance(0.5, 0.5)
    pp.transform(at)
    p.append(pp, false)
    pp.reset()
    fun(pp)
//    at.setToScale(0.667, 0.667)
//    at.translate(32 * 0.333, 32 * 0.333)
    at.setToTranslation(32 * 0.25, 32 * 0.25)
    at.scale(0.75, 0.75)
    pp.transform(at)
    p.append(pp, false)
  }

  def RealNumber(p: Path2D): Unit = {
    p.moveTo(29.892f, 27.416f)
    p.curveTo(29.136002f, 27.272001f, 27.804f, 26.300001f, 27.408f, 25.796f)
    p.lineTo(20.136002f, 17.119999f)
    p.curveTo(24.167997f, 16.004f, 25.932001f, 13.951999f, 25.932001f, 10.495998f)
    p.curveTo(25.932001f, 6.6079984f, 22.511995f, 4.3039985f, 17.760002f, 4.3039985f)
    p.lineTo(3.2160025f, 4.3039985f)
    p.lineTo(3.2160025f, 5.8879986f)
    p.lineTo(4.6920023f, 5.9959984f)
    p.curveTo(5.736001f, 6.0679984f, 5.8080025f, 6.4279985f, 5.8080025f, 8.443998f)
    p.lineTo(5.8080025f, 24.859999f)
    p.curveTo(5.8080025f, 26.911999f, 5.7360015f, 27.235998f, 4.7280025f, 27.307999f)
    p.lineTo(3.0000024f, 27.415998f)
    p.lineTo(3.0000024f, 28.999998f)
    p.lineTo(14.700003f, 28.999998f)
    p.lineTo(14.700003f, 27.415998f)
    p.lineTo(12.972003f, 27.307999f)
    p.curveTo(11.9640045f, 27.235998f, 11.892003f, 26.911999f, 11.892003f, 24.859999f)
    p.lineTo(11.892003f, 5.8879986f)
    p.lineTo(15.348003f, 5.8879986f)
    p.curveTo(18.624f, 5.8879986f, 19.668003f, 8.623999f, 19.668003f, 11.251999f)
    p.curveTo(19.668003f, 14.095999f, 17.220001f, 16.147999f, 14.736003f, 16.22f)
    p.lineTo(13.368003f, 16.22f)
    p.lineTo(12.936003f, 18.164f)
    p.lineTo(22.116003f, 29.0f)
    p.lineTo(29.532003f, 29.0f)
    p.lineTo(29.892004f, 27.416f)
    p.moveTo(24.348003f, 10.496f)
    p.curveTo(24.348003f, 13.016001f, 23.088001f, 14.456f, 19.632004f, 15.5f)
    p.curveTo(20.604002f, 14.384f, 21.252005f, 12.908f, 21.252005f, 11.252f)
    p.curveTo(21.252005f, 9.308f, 20.784004f, 7.364f, 19.560005f, 6.032f)
    p.curveTo(22.368002f, 6.464f, 24.348005f, 7.976f, 24.348005f, 10.496f)
    p.moveTo(26.832005f, 27.416f)
    p.lineTo(22.908005f, 27.416f)
    p.lineTo(14.808004f, 17.804f)
    p.curveTo(15.960003f, 17.768002f, 17.184006f, 17.552f, 18.228004f, 17.336f)
    p.lineTo(26.220005f, 26.804f)
    p.curveTo(26.364004f, 26.984001f, 26.580006f, 27.2f, 26.832005f, 27.416f)
    p.moveTo(10.488005f, 27.416f)
    p.lineTo(7.2120047f, 27.416f)
    p.curveTo(7.3560047f, 26.84f, 7.3920045f, 26.012001f, 7.3920045f, 24.86f)
    p.lineTo(7.3920045f, 8.444f)
    p.curveTo(7.3920045f, 7.2920003f, 7.3560047f, 6.464f, 7.2120047f, 5.8880005f)
    p.lineTo(10.308004f, 5.8880005f)
    p.lineTo(10.308004f, 24.86f)
    p.curveTo(10.308004f, 26.012001f, 10.344005f, 26.84f, 10.488005f, 27.416f)
  }

  def IntegerNumber(p: Path2D): Unit = {
    p.moveTo(26.676f, 6.3561f)
    p.lineTo(26.676f, 4.3041f)
    p.lineTo(5.6879997f, 4.3041f)
    p.lineTo(6.2999997f, 11.576099f)
    p.lineTo(7.884f, 11.4681f)
    p.lineTo(7.848f, 10.7841f)
    p.curveTo(7.8120003f, 9.632099f, 9.648005f, 5.8880997f, 13.464001f, 5.8880997f)
    p.lineTo(19.404001f, 5.8880997f)
    p.curveTo(18.900002f, 7.2921f, 17.748001f, 9.1641f, 16.740002f, 10.424099f)
    p.lineTo(8.208002f, 22.6281f)
    p.curveTo(7.452002f, 23.7081f, 4.500002f, 26.876099f, 4.500002f, 26.876099f)
    p.lineTo(4.500002f, 29.0001f)
    p.lineTo(26.532001f, 29.0001f)
    p.lineTo(26.532001f, 20.6841f)
    p.lineTo(24.948002f, 20.6841f)
    p.lineTo(24.948002f, 21.296099f)
    p.curveTo(24.948002f, 23.4201f, 23.111998f, 27.4161f, 18.216002f, 27.4161f)
    p.lineTo(12.312002f, 27.4161f)
    p.lineTo(26.676003f, 6.3561f)
    p.moveTo(9.396002f, 5.7801f)
    p.curveTo(8.568003f, 6.4641f, 8.064001f, 7.0761f, 7.488002f, 7.8321f)
    p.lineTo(7.308002f, 5.7801f)
    p.lineTo(9.396002f, 5.7801f)
    p.moveTo(25.092003f, 5.8880997f)
    p.lineTo(10.440002f, 27.4161f)
    p.lineTo(6.1920023f, 27.4161f)
    p.curveTo(6.5880013f, 26.9841f, 8.784002f, 24.5721f, 9.504003f, 23.5641f)
    p.lineTo(18.000004f, 11.3961f)
    p.curveTo(19.044003f, 9.9201f, 20.268003f, 7.9041f, 20.988003f, 5.8881f)
    p.lineTo(25.092003f, 5.8881f)
    p.moveTo(25.056004f, 27.5241f)
    p.lineTo(23.004004f, 27.5241f)
    p.curveTo(23.904003f, 26.876099f, 24.444004f, 26.300098f, 25.056004f, 25.4361f)
    p.lineTo(25.056004f, 27.5241f)
  }

  def BooleanNumber(p: Path2D): Unit = {
    p.moveTo(3.21875f, 4.3125f)
    p.lineTo(3.21875f, 5.875f)
    p.lineTo(4.6875f, 6.0f)
    p.curveTo(5.7314987f, 6.072f, 5.8125f, 6.4215f, 5.8125f, 8.4375f)
    p.lineTo(5.8125f, 24.875f)
    p.curveTo(5.8125f, 26.891f, 5.7315f, 27.2405f, 4.6875f, 27.3125f)
    p.lineTo(3.21875f, 27.40625f)
    p.lineTo(3.21875f, 29.0f)
    p.lineTo(17.75f, 29.0f)
    p.curveTo(22.501995f, 29.0f, 25.9375f, 26.7005f, 25.9375f, 22.8125f)
    p.curveTo(25.9375f, 19.7885f, 24.573172f, 17.84409f, 21.53125f, 16.65625f)
    p.curveTo(24.573172f, 15.46841f, 25.9375f, 13.524f, 25.9375f, 10.5f)
    p.curveTo(25.9375f, 6.612f, 22.501995f, 4.3125f, 17.75f, 4.3125f)
    p.lineTo(3.21875f, 4.3125f)
    p.moveTo(7.21875f, 5.875f)
    p.lineTo(10.3125f, 5.875f)
    p.lineTo(10.3125f, 27.40625f)
    p.lineTo(7.21875f, 27.40625f)
    p.curveTo(7.36275f, 26.83025f, 7.40625f, 26.027f, 7.40625f, 24.875f)
    p.lineTo(7.40625f, 8.4375f)
    p.curveTo(7.40625f, 7.2855f, 7.36275f, 6.451f, 7.21875f, 5.875f)
    p.lineTo(7.21875f, 5.875f)
    p.moveTo(11.90625f, 5.875f)
    p.lineTo(15.34375f, 5.875f)
    p.curveTo(18.619747f, 5.875f, 19.65625f, 8.622f, 19.65625f, 11.25f)
    p.curveTo(19.65625f, 13.288841f, 18.401312f, 14.900214f, 16.78125f, 15.6875f)
    p.lineTo(14.25f, 16.65625f)
    p.lineTo(16.78125f, 17.59375f)
    p.curveTo(18.407566f, 18.37864f, 19.65625f, 20.01828f, 19.65625f, 22.0625f)
    p.curveTo(19.65625f, 24.6905f, 18.619747f, 27.40625f, 15.34375f, 27.40625f)
    p.lineTo(11.90625f, 27.40625f)
    p.lineTo(11.90625f, 5.875f)
    p.moveTo(19.5625f, 6.03125f)
    p.curveTo(22.370497f, 6.46325f, 24.34375f, 7.98f, 24.34375f, 10.5f)
    p.curveTo(24.34375f, 13.02f, 23.080997f, 14.456f, 19.625f, 15.5f)
    p.curveTo(20.596998f, 14.384f, 21.25f, 12.906f, 21.25f, 11.25f)
    p.curveTo(21.25f, 9.306f, 20.786499f, 7.36325f, 19.5625f, 6.03125f)
    p.lineTo(19.5625f, 6.03125f)
    p.moveTo(19.625f, 17.8125f)
    p.curveTo(23.080997f, 18.8565f, 24.34375f, 20.2925f, 24.34375f, 22.8125f)
    p.curveTo(24.34375f, 25.3325f, 22.370497f, 26.84925f, 19.5625f, 27.28125f)
    p.curveTo(20.786499f, 25.94925f, 21.25f, 24.0065f, 21.25f, 22.0625f)
    p.curveTo(21.25f, 20.4065f, 20.596998f, 18.9285f, 19.625f, 17.8125f)
    p.lineTo(19.625f, 17.8125f)
  }

  def RealNumberVector(p: Path2D): Unit = {
    p.moveTo(30.0f, 1.9999998f)
    p.lineTo(23.273743f, 1.9999998f)
    p.lineTo(23.273743f, 2.9072623f)
    p.curveTo(27.591057f, 3.2513962f, 27.872625f, 3.7206728f, 27.872625f, 6.442458f)
    p.lineTo(27.872625f, 25.55754f)
    p.curveTo(27.872625f, 28.279325f, 27.559772f, 28.748604f, 23.273743f, 29.061451f)
    p.lineTo(23.273743f, 30.0f)
    p.lineTo(30.0f, 30.0f)
    p.lineTo(30.0f, 1.9999998f)
    p.moveTo(1.9999999f, 30.0f)
    p.lineTo(8.726256f, 30.0f)
    p.lineTo(8.726256f, 29.092737f)
    p.curveTo(4.4089427f, 28.748604f, 4.127374f, 28.279325f, 4.127374f, 25.55754f)
    p.lineTo(4.127374f, 6.442457f)
    p.curveTo(4.127374f, 3.7206724f, 4.4402275f, 3.2513955f, 8.726257f, 2.9385467f)
    p.lineTo(8.726257f, 1.9999992f)
    p.lineTo(2.0000005f, 1.9999992f)
    p.lineTo(2.0000005f, 30.0f)
    p.moveTo(27.2028f, 25.2632f)
    p.curveTo(26.598001f, 25.148f, 25.5324f, 24.3704f, 25.2156f, 23.9672f)
    p.lineTo(19.398f, 17.0264f)
    p.curveTo(22.623598f, 16.1336f, 24.034801f, 14.492f, 24.034801f, 11.7272f)
    p.curveTo(24.034801f, 8.616799f, 21.298798f, 6.7735996f, 17.497202f, 6.7735996f)
    p.lineTo(5.8620024f, 6.7735996f)
    p.lineTo(5.8620024f, 8.0408f)
    p.lineTo(7.0428023f, 8.1272f)
    p.curveTo(7.8780017f, 8.1848f, 7.935602f, 8.4728f, 7.935602f, 10.0856f)
    p.lineTo(7.935602f, 23.2184f)
    p.curveTo(7.935602f, 24.859999f, 7.878001f, 25.1192f, 7.0716023f, 25.1768f)
    p.lineTo(5.6892023f, 25.263199f)
    p.lineTo(5.6892023f, 26.5304f)
    p.lineTo(15.049202f, 26.5304f)
    p.lineTo(15.049202f, 25.263199f)
    p.lineTo(13.666801f, 25.1768f)
    p.curveTo(12.860402f, 25.1192f, 12.802801f, 24.86f, 12.802801f, 23.2184f)
    p.lineTo(12.802801f, 8.040799f)
    p.lineTo(15.567601f, 8.040799f)
    p.curveTo(18.1884f, 8.040799f, 19.023602f, 10.229599f, 19.023602f, 12.331999f)
    p.curveTo(19.023602f, 14.607199f, 17.065199f, 16.248798f, 15.078001f, 16.306398f)
    p.lineTo(13.983601f, 16.306398f)
    p.lineTo(13.6380005f, 17.861599f)
    p.lineTo(20.982f, 26.5304f)
    p.lineTo(26.9148f, 26.5304f)
    p.lineTo(27.2028f, 25.263199f)
    p.moveTo(22.767601f, 11.727199f)
    p.curveTo(22.767601f, 13.743198f, 21.7596f, 14.895199f, 18.9948f, 15.730398f)
    p.curveTo(19.7724f, 14.837598f, 20.2908f, 13.656798f, 20.2908f, 12.331998f)
    p.curveTo(20.2908f, 10.776798f, 19.916399f, 9.221598f, 18.9372f, 8.155998f)
    p.curveTo(21.183598f, 8.501598f, 22.767601f, 9.711198f, 22.767601f, 11.727198f)
    p.moveTo(24.7548f, 25.263199f)
    p.lineTo(21.6156f, 25.263199f)
    p.lineTo(15.135601f, 17.573599f)
    p.curveTo(16.0572f, 17.5448f, 17.036402f, 17.372f, 17.871601f, 17.1992f)
    p.lineTo(24.265202f, 24.7736f)
    p.curveTo(24.380402f, 24.917599f, 24.553202f, 25.090399f, 24.7548f, 25.263199f)
    p.moveTo(11.679601f, 25.263199f)
    p.lineTo(9.058801f, 25.263199f)
    p.curveTo(9.174001f, 24.802399f, 9.202801f, 24.14f, 9.202801f, 23.2184f)
    p.lineTo(9.202801f, 10.085599f)
    p.curveTo(9.202801f, 9.163999f, 9.174001f, 8.501599f, 9.058801f, 8.040799f)
    p.lineTo(11.535601f, 8.040799f)
    p.lineTo(11.535601f, 23.2184f)
    p.curveTo(11.535601f, 24.14f, 11.564401f, 24.802399f, 11.679601f, 25.263199f)
  }

  def IntegerNumberVector(p: Path2D): Unit = {
    p.moveTo(30.0f, 1.9999998f)
    p.lineTo(23.273743f, 1.9999998f)
    p.lineTo(23.273743f, 2.9072623f)
    p.curveTo(27.59106f, 3.2513964f, 27.872625f, 3.7206702f, 27.872625f, 6.442458f)
    p.lineTo(27.872625f, 25.55754f)
    p.curveTo(27.872625f, 28.27933f, 27.559776f, 28.748604f, 23.273743f, 29.061451f)
    p.lineTo(23.273743f, 30.0f)
    p.lineTo(30.0f, 30.0f)
    p.lineTo(30.0f, 1.9999998f)
    p.moveTo(1.9999999f, 30.0f)
    p.lineTo(8.726256f, 30.0f)
    p.lineTo(8.726256f, 29.092737f)
    p.curveTo(4.4089384f, 28.748604f, 4.127374f, 28.27933f, 4.127374f, 25.55754f)
    p.lineTo(4.127374f, 6.4424577f)
    p.curveTo(4.127374f, 3.72067f, 4.440223f, 3.2513962f, 8.726257f, 2.9385471f)
    p.lineTo(8.726257f, 1.9999998f)
    p.lineTo(2.0000005f, 1.9999998f)
    p.lineTo(1.9999999f, 30.0f)

    p.moveTo(24.8704f, 7.7632f)
    p.lineTo(24.8704f, 6.1215997f)
    p.lineTo(8.08f, 6.1215997f)
    p.lineTo(8.5696f, 11.939199f)
    p.lineTo(9.8368f, 11.852799f)
    p.lineTo(9.808f, 11.305599f)
    p.curveTo(9.7792f, 10.384f, 11.248003f, 7.3888f, 14.3008f, 7.3888f)
    p.lineTo(19.0528f, 7.3888f)
    p.curveTo(18.6496f, 8.512f, 17.727999f, 10.0096f, 16.9216f, 11.0176f)
    p.lineTo(10.096001f, 20.7808f)
    p.curveTo(9.4912f, 21.6448f, 7.1296005f, 24.1792f, 7.1296005f, 24.1792f)
    p.lineTo(7.1296005f, 25.878399f)
    p.lineTo(24.7552f, 25.878399f)
    p.lineTo(24.7552f, 19.2256f)
    p.lineTo(23.488f, 19.2256f)
    p.lineTo(23.488f, 19.715199f)
    p.curveTo(23.488f, 21.414398f, 22.019197f, 24.611198f, 18.102402f, 24.611198f)
    p.lineTo(13.3792f, 24.611198f)
    p.lineTo(24.8704f, 7.7632f)
    p.moveTo(11.046399f, 7.3023996f)
    p.curveTo(10.384f, 7.8496f, 9.980799f, 8.3392f, 9.519999f, 8.943999f)
    p.lineTo(9.3759985f, 7.302399f)
    p.lineTo(11.0464f, 7.302399f)
    p.moveTo(23.603199f, 7.388799f)
    p.lineTo(11.8816f, 24.6112f)
    p.lineTo(8.4832f, 24.6112f)
    p.curveTo(8.799999f, 24.2656f, 10.5568f, 22.336f, 11.1328f, 21.5296f)
    p.lineTo(17.9296f, 11.7952f)
    p.curveTo(18.7648f, 10.6144f, 19.744f, 9.0016f, 20.32f, 7.3888f)
    p.lineTo(23.603199f, 7.3888f)
    p.moveTo(23.5744f, 24.6976f)
    p.lineTo(21.9328f, 24.6976f)
    p.curveTo(22.6528f, 24.1792f, 23.0848f, 23.7184f, 23.5744f, 23.027199f)
    p.lineTo(23.5744f, 24.6976f)
  }

  def Pointer(p: Path2D): Unit = {
    p.moveTo(15.0f, 24.9999f)
    p.lineTo(21.0f, 24.9999f)
    p.lineTo(21.0f, 27.9999f)
    p.lineTo(15.0f, 27.9999f)
    p.lineTo(15.0f, 24.9999f)
    p.moveTo(7.0f, 24.9999f)
    p.lineTo(13.0f, 24.9999f)
    p.lineTo(13.0f, 27.9999f)
    p.lineTo(7.0f, 27.9999f)
    p.lineTo(7.0f, 24.9999f)
    p.moveTo(15.0f, 2.9999008f)
    p.lineTo(21.0f, 2.9999008f)
    p.lineTo(21.0f, 5.999901f)
    p.lineTo(15.0f, 5.999901f)
    p.lineTo(15.0f, 2.9999008f)
    p.moveTo(15.347826f, 6.0f)
    p.lineTo(15.347826f, 25.0f)
    p.lineTo(12.347826f, 25.0f)
    p.lineTo(12.347826f, 6.0f)
    p.lineTo(15.347826f, 6.0f)
    p.moveTo(7.0f, 2.9999f)
    p.lineTo(13.0f, 2.9999f)
    p.lineTo(13.0f, 5.9999f)
    p.lineTo(7.0f, 5.9999f)
    p.lineTo(7.0f, 2.9999f)
  }

  def Gain(p: Path2D): Unit = {
    p.moveTo(31.5f, 6.5f)
    p.lineTo(29.25f, 7.96875f)
    p.lineTo(4.75f, 23.75f)
    p.lineTo(0.5f, 26.5f)
    p.lineTo(5.5625f, 26.5f)
    p.lineTo(30.03125f, 26.5f)
    p.lineTo(31.5f, 26.5f)
    p.lineTo(31.5f, 25.0f)
    p.lineTo(31.5f, 9.21875f)
    p.lineTo(31.5f, 6.5f)
    p.lineTo(31.5f, 6.5f)
    p.moveTo(28.5625f, 11.90625f)
    p.lineTo(28.5625f, 23.5f)
    p.lineTo(18.90625f, 23.5f)
    p.lineTo(18.90625f, 18.125f)
    p.lineTo(28.5625f, 11.90625f)
    p.lineTo(28.5625f, 11.90625f)
  }

  def Patch(p: Path2D): Unit = {
    p.moveTo(16.00732f, 2.5002022f)
    p.curveTo(8.587009f, 2.5002022f, 2.4910898f, 8.567303f, 2.4910898f, 15.987613f)
    p.curveTo(2.4910898f, 23.407925f, 8.587009f, 29.503843f, 16.00732f, 29.503843f)
    p.curveTo(23.427631f, 29.503843f, 29.494732f, 23.407923f, 29.494732f, 15.987613f)
    p.curveTo(29.494732f, 8.567303f, 23.427631f, 2.5002022f, 16.00732f, 2.5002022f)
    p.lineTo(16.00732f, 2.5002022f)
    p.moveTo(16.00732f, 8.494607f)
    p.curveTo(20.18499f, 8.494607f, 23.500326f, 11.809944f, 23.500326f, 15.987614f)
    p.curveTo(23.500326f, 20.165283f, 20.18499f, 23.50944f, 16.00732f, 23.50944f)
    p.curveTo(11.829651f, 23.50944f, 8.514315f, 20.165283f, 8.514315f, 15.987614f)
    p.curveTo(8.514315f, 11.809944f, 11.829651f, 8.494608f, 16.00732f, 8.494608f)
    p.lineTo(16.00732f, 8.494607f)
  }

  def Mute(p: Path2D): Unit = {
    p.moveTo(18.34375f, 1.1875f)
    p.lineTo(8.375f, 10.21875f)
    p.lineTo(1.0f, 10.71875f)
    p.lineTo(1.0f, 21.09375f)
    p.lineTo(8.375f, 21.8125f)
    p.lineTo(18.34375f, 30.625f)
    p.lineTo(18.34375f, 1.1875f)
    p.lineTo(18.34375f, 1.1875f)
    p.moveTo(22.21875f, 10.375f)
    p.lineTo(20.0f, 12.59375f)
    p.lineTo(23.3125f, 15.90625f)
    p.lineTo(20.0f, 19.21875f)
    p.lineTo(22.21875f, 21.4375f)
    p.lineTo(25.5f, 18.125f)
    p.lineTo(28.8125f, 21.4375f)
    p.lineTo(31.03125f, 19.21875f)
    p.lineTo(27.71875f, 15.90625f)
    p.lineTo(31.03125f, 12.59375f)
    p.lineTo(28.8125f, 10.375f)
    p.lineTo(25.5f, 13.6875f)
    p.lineTo(22.21875f, 10.375f)
    p.lineTo(22.21875f, 10.375f)
  }

  def Crop(p: Path2D): Unit = {
    p.moveTo(21.65625f, 26.28125f)
    p.lineTo(21.65625f, 29.875f)
    p.lineTo(25.28125f, 29.875f)
    p.lineTo(25.28125f, 26.28125f)
    p.lineTo(21.65625f, 26.28125f)
    p.moveTo(12.0f, 7.125f)
    p.lineTo(12.0f, 10.75f)
    p.lineTo(21.65625f, 10.75f)
    p.lineTo(21.65625f, 19.1875f)
    p.lineTo(25.28125f, 19.1875f)
    p.lineTo(25.28125f, 7.125f)
    p.lineTo(12.0f, 7.125f)
    p.moveTo(1.28125f, 7.125f)
    p.lineTo(1.28125f, 10.75f)
    p.lineTo(4.90625f, 10.75f)
    p.lineTo(4.90625f, 7.125f)
    p.lineTo(1.28125f, 7.125f)
    p.moveTo(6.3930154f, 1.4597483f)
    p.lineTo(10.538234f, 1.4597483f)
    p.lineTo(10.538234f, 20.635075f)
    p.lineTo(30.961018f, 20.635075f)
    p.lineTo(30.961018f, 24.772884f)
    p.lineTo(6.393017f, 24.772884f)
    p.lineTo(6.3930154f, 1.4597483f)
  }

  def Aperture(p: Path2D): Unit = {
    p.moveTo(11.433359f, 15.305405f)
    p.lineTo(3.0f, 20.170374f)
    p.lineTo(3.0f, 23.637968f)
    p.lineTo(11.430403f, 28.50087f)
    p.lineTo(11.433359f, 15.305405f)
    p.moveTo(3.0f, 18.36687f)
    p.lineTo(14.43611f, 11.768991f)
    p.lineTo(6.003638f, 6.904318f)
    p.lineTo(3.000591f, 8.636638f)
    p.lineTo(3.0f, 18.36687f)
    p.moveTo(29.0f, 13.906848f)
    p.lineTo(17.488813f, 20.547852f)
    p.lineTo(25.92365f, 25.412523f)
    p.lineTo(29.0f, 23.637966f)
    p.lineTo(29.0f, 13.906848f)
    p.moveTo(24.360352f, 26.314571f)
    p.lineTo(12.99577f, 19.759521f)
    p.lineTo(12.99367f, 29.403507f)
    p.lineTo(16.000263f, 31.137302f)
    p.lineTo(24.360352f, 26.314571f)
    p.moveTo(19.004229f, 2.8699176f)
    p.lineTo(16.000296f, 1.1373024f)
    p.lineTo(7.5669365f, 6.0025663f)
    p.lineTo(19.004229f, 12.600741f)
    p.lineTo(19.004229f, 2.8699176f)
    p.moveTo(20.567528f, 16.968018f)
    p.lineTo(29.0f, 12.103345f)
    p.lineTo(29.0f, 8.636638f)
    p.lineTo(20.567528f, 3.7719646f)
    p.lineTo(20.567528f, 16.968018f)
  }

  def Markdown(p: Path2D): Unit = {
    p.moveTo(2.3085938f, 6.3085938f)
    p.curveTo(1.043438f, 6.3085938f, 0.0f, 7.3500843f, 0.0f, 8.615234f)
    p.lineTo(0.0f, 23.691406f)
    p.curveTo(0.0f, 24.956566f, 1.043438f, 26.0f, 2.3085938f, 26.0f)
    p.lineTo(29.691406f, 26.0f)
    p.curveTo(30.95656f, 26.0f, 32.0f, 24.956566f, 32.0f, 23.691406f)
    p.lineTo(32.0f, 8.615234f)
    p.curveTo(32.0f, 7.3500843f, 30.95656f, 6.3085938f, 29.691406f, 6.3085938f)
    p.lineTo(2.3085938f, 6.3085938f)
    p.moveTo(2.3085938f, 7.845703f)
    p.lineTo(29.691406f, 7.845703f)
    p.curveTo(30.130867f, 7.845703f, 30.460938f, 8.175775f, 30.460938f, 8.615234f)
    p.lineTo(30.460938f, 23.691406f)
    p.curveTo(30.460938f, 24.130867f, 30.130865f, 24.460938f, 29.691406f, 24.460938f)
    p.lineTo(2.3085938f, 24.460938f)
    p.curveTo(1.8691344f, 24.460938f, 1.5390625f, 24.130865f, 1.5390625f, 23.691406f)
    p.lineTo(1.5390625f, 8.615234f)
    p.curveTo(1.5390625f, 8.175775f, 1.8691344f, 7.845703f, 2.3085938f, 7.845703f)
    p.lineTo(2.3085938f, 7.845703f)
    p.moveTo(4.6152344f, 10.923828f)
    p.lineTo(4.6152344f, 21.384766f)
    p.lineTo(7.6914062f, 21.384766f)
    p.lineTo(7.6914062f, 15.384766f)
    p.lineTo(10.769531f, 19.230469f)
    p.lineTo(13.845703f, 15.384766f)
    p.lineTo(13.845703f, 21.384766f)
    p.lineTo(16.923828f, 21.384766f)
    p.lineTo(16.923828f, 10.923828f)
    p.lineTo(13.845703f, 10.923828f)
    p.lineTo(10.769531f, 14.769531f)
    p.lineTo(7.6914062f, 10.923828f)
    p.lineTo(4.6152344f, 10.923828f)
    p.moveTo(22.308594f, 10.923828f)
    p.lineTo(22.308594f, 16.308594f)
    p.lineTo(19.230469f, 16.308594f)
    p.lineTo(23.845703f, 21.384766f)
    p.lineTo(28.460938f, 16.308594f)
    p.lineTo(25.384766f, 16.308594f)
    p.lineTo(25.384766f, 10.923828f)
    p.lineTo(22.308594f, 10.923828f)
  }

  /////////////////////////////////////////////////////////////
  // the following shapes are taken from the Noun project
  // https://thenounproject.com/term/sparks/409130/
  //
  // Creative Commons CC BY 3.0 US
  //
  // Copyright (c) Bohdan Burmich

  def Sparks(p: Path2D): Unit = {
    p.moveTo(25.6875f, 1.125f)
    p.curveTo(23.937378f, 3.1348333f, 20.884834f, 3.3438723f, 18.875f, 1.59375f)
    p.curveTo(20.8838f, 3.3428388f, 20.623056f, 6.1773224f, 18.875f, 8.1875f)
    p.lineTo(0.5f, 31.0f)
    p.lineTo(19.78125f, 8.96875f)
    p.curveTo(21.532063f, 6.9582276f, 24.146072f, 6.187033f, 26.15625f, 7.9375f)
    p.curveTo(24.146418f, 6.187033f, 23.937378f, 3.1348333f, 25.6875f, 1.125f)
    p.lineTo(25.6875f, 1.125f)
    p.moveTo(0.5f, 31.0f)
    p.lineTo(12.65625f, 23.0625f)
    p.lineTo(12.96875f, 22.84375f)
    p.curveTo(14.275572f, 22.05945f, 15.769211f, 22.103657f, 16.6875f, 23.375f)
    p.curveTo(15.6924f, 21.996878f, 15.996879f, 20.0576f, 17.375f, 19.0625f)
    p.curveTo(15.996879f, 20.0576f, 14.089194f, 19.75312f, 13.09375f, 18.375f)
    p.curveTo(14.089194f, 19.75312f, 13.472217f, 21.223307f, 12.09375f, 22.21875f)
    p.lineTo(0.5f, 31.0f)
    p.moveTo(0.5f, 31.0f)
    p.lineTo(26.0f, 21.71875f)
    p.curveTo(27.556543f, 21.11735f, 29.179506f, 21.3813f, 29.78125f, 22.9375f)
    p.curveTo(29.179506f, 21.3813f, 29.9438f, 19.63265f, 31.5f, 19.03125f)
    p.curveTo(29.9438f, 19.63265f, 28.19515f, 18.83745f, 27.59375f, 17.28125f)
    p.curveTo(28.194803f, 18.83676f, 27.148916f, 20.117004f, 25.59375f, 20.71875f)
    p.lineTo(0.5f, 31.0f)
    p.moveTo(0.5f, 31.0f)
    p.lineTo(21.15625f, 27.40625f)
    p.curveTo(22.419672f, 27.161005f, 23.5676f, 27.58033f, 23.8125f, 28.84375f)
    p.curveTo(23.567257f, 27.58033f, 24.393517f, 26.3699f, 25.65625f, 26.125f)
    p.curveTo(24.393517f, 26.3699f, 23.182743f, 25.544674f, 22.9375f, 24.28125f)
    p.curveTo(23.182743f, 25.544329f, 22.294329f, 26.348162f, 21.03125f, 26.59375f)
    p.lineTo(0.5f, 31.0f)
    p.moveTo(0.5f, 31.0f)
    p.lineTo(10.8125f, 12.34375f)
    p.curveTo(11.457645f, 11.152317f, 12.549456f, 10.4207115f, 13.6875f, 10.75f)
    p.curveTo(13.845256f, 10.80029f, 13.981712f, 10.859656f, 14.125f, 10.9375f)
    p.curveTo(12.831267f, 10.236556f, 12.393745f, 8.599877f, 13.125f, 7.25f)
    p.curveTo(12.394089f, 8.599877f, 10.731577f, 9.106505f, 9.4375f, 8.40625f)
    p.curveTo(10.731577f, 9.106505f, 10.856255f, 10.650121f, 10.125f, 12.0f)
    p.lineTo(0.5f, 31.0f)
  }

  /////////////////////////////////////////////////////////////
  // the following shapes are taken from the Open-Iconic project
  // https://github.com/iconic/open-iconic
  //
  // The MIT License (MIT)
  //
  // Copyright (c) 2014 Waybury

  def Share(p: Path2D): Unit = {
    p.moveTo(20.0f, 0.0f)
    p.lineTo(20.0f, 8.0f)
    p.curveTo(4.0f, 8.0f, 0.0f, 16.2f, 0.0f, 28.0f)
    p.curveTo(2.08f, 20.08f, 8.0f, 16.0f, 16.0f, 16.0f)
    p.lineTo(20.0f, 16.0f)
    p.lineTo(20.0f, 24.0f)
    p.lineTo(32.0f, 11.36f)
    p.lineTo(20.0f, 0.0f)
    p.lineTo(20.0f, 0.0f)
  }

  /////////////////////////////////////////////////////////
  // the following shapes were taken from the public domain

  def Audition(p: Path2D): Unit = {
    p.moveTo(9.65233f, 30.7985f)
    p.curveTo(7.200371f, 30.2279f, 5.29346f, 27.9519f, 5.5179763f, 25.5935f)
    p.curveTo(5.419949f, 24.1758f, 5.9630218f, 22.581f, 7.2916408f, 21.7735f)
    p.curveTo(9.282202f, 21.5046f, 8.280723f, 23.9227f, 7.5695863f, 24.7191f)
    p.curveTo(6.6885376f, 26.180601f, 7.8139067f, 27.8276f, 9.137316f, 28.6636f)
    p.curveTo(10.270974f, 29.359201f, 11.894443f, 29.393702f, 13.081552f, 28.789202f)
    p.curveTo(15.0968275f, 27.437601f, 14.753905f, 24.735f, 16.409454f, 23.131102f)
    p.curveTo(19.090185f, 20.143402f, 22.262564f, 17.306501f, 23.64919f, 13.550101f)
    p.curveTo(24.750463f, 10.398501f, 24.010921f, 6.3739014f, 20.871498f, 4.434101f)
    p.curveTo(18.733377f, 3.1227f, 15.922653f, 3.0635f, 13.518707f, 3.7024f)
    p.curveTo(10.51111f, 4.6221f, 8.717081f, 7.5652f, 8.750963f, 10.4014f)
    p.curveTo(8.58954f, 11.511f, 8.879605f, 12.7671f, 8.33096f, 13.7733f)
    p.curveTo(7.01323f, 13.7603f, 7.069003f, 11.9344f, 6.9901676f, 11.002f)
    p.curveTo(6.915446f, 9.0379f, 7.121056f, 6.9872f, 8.315855f, 5.2976f)
    p.curveTo(9.926475f, 2.6852f, 13.086461f, 0.7577f, 16.416458f, 1.0247f)
    p.curveTo(19.092913f, 0.95070004f, 21.8927f, 1.6963f, 23.744589f, 3.5630002f)
    p.curveTo(25.808434f, 5.3167f, 26.576733f, 7.9691f, 26.49125f, 10.489f)
    p.curveTo(26.58322f, 12.776501f, 25.9543f, 15.1106f, 24.416983f, 16.9379f)
    p.curveTo(22.342613f, 19.6002f, 19.588581f, 21.7168f, 17.320526f, 24.2241f)
    p.curveTo(16.491312f, 26.2726f, 16.230124f, 28.7631f, 14.153629f, 30.1443f)
    p.curveTo(12.877369f, 31.028801f, 11.161746f, 31.191101f, 9.65233f, 30.7985f)
    p.lineTo(9.65233f, 30.7985f)
    p.moveTo(11.009423f, 21.914f)
    p.curveTo(9.442545f, 21.198399f, 9.56549f, 19.1355f, 10.638026f, 18.0828f)
    p.curveTo(11.536209f, 17.0756f, 12.919971f, 15.8569f, 12.330531f, 14.4255f)
    p.curveTo(11.60995f, 13.183599f, 10.04557f, 12.2874f, 10.0975895f, 10.7261f)
    p.curveTo(9.8528595f, 8.3147f, 11.535688f, 5.8281f, 14.082627f, 5.1489f)
    p.curveTo(15.720551f, 4.9824f, 17.524397f, 4.9655f, 18.893496f, 5.9531f)
    p.curveTo(21.092365f, 7.2374f, 21.632265f, 9.9523f, 20.89761f, 12.1273f)
    p.curveTo(20.498234f, 13.255f, 20.00512f, 14.5562f, 18.83373f, 15.1752f)
    p.curveTo(17.473064f, 14.740601f, 19.31447f, 13.046901f, 19.381874f, 12.1358f)
    p.curveTo(20.14839f, 9.990801f, 18.92142f, 7.1923003f, 16.391724f, 6.6990004f)
    p.curveTo(14.289095f, 6.3548f, 12.077779f, 8.1243f, 12.389763f, 10.1237f)
    p.curveTo(13.110992f, 12.3286f, 16.002073f, 13.3998f, 16.281347f, 15.8032f)
    p.curveTo(16.42697f, 17.9041f, 15.431056f, 20.0952f, 13.698548f, 21.4644f)
    p.curveTo(12.936242f, 21.965f, 11.9122715f, 22.3632f, 11.009421f, 21.914f)
    p.lineTo(11.009423f, 21.914f)
  }

  /////////////////////////////////////////////////////////////
  // the following shapes are taken from the Noun project
  // https://thenounproject.com/term/justice/643699/
  //
  // Creative Commons CC BY 3.0 US
  //
  // Copyright (c) Alex Fuller

  def Pattern(p: Path2D): Unit = {
    p.moveTo(4.0f, 4.4804688f)
    p.lineTo(4.0f, 27.519531f)
    p.lineTo(4.4804688f, 27.519531f)
    p.lineTo(4.9609375f, 27.519531f)
    p.lineTo(7.359375f, 27.519531f)
    p.curveTo(7.359375f, 26.092651f, 6.3228574f, 24.912394f, 4.9609375f, 24.683594f)
    p.lineTo(4.9609375f, 18.835938f)
    p.curveTo(6.3225374f, 18.606497f, 7.359375f, 17.4272f, 7.359375f, 16.0f)
    p.curveTo(7.359375f, 14.57344f, 6.3225374f, 13.391229f, 4.9609375f, 13.162109f)
    p.lineTo(4.9609375f, 7.3164062f)
    p.curveTo(6.3228574f, 7.0879264f, 7.361328f, 5.9070287f, 7.361328f, 4.4804688f)
    p.lineTo(4.4804688f, 4.4804688f)
    p.lineTo(4.0f, 4.4804688f)
    p.moveTo(9.759766f, 4.4804688f)
    p.lineTo(9.759766f, 7.4023438f)
    p.curveTo(8.398166f, 7.6314635f, 7.359375f, 8.813674f, 7.359375f, 10.240234f)
    p.curveTo(7.359375f, 11.666795f, 8.398166f, 12.847053f, 9.759766f, 13.076172f)
    p.lineTo(9.759766f, 18.923828f)
    p.curveTo(8.398166f, 19.152628f, 7.359375f, 20.332886f, 7.359375f, 21.759766f)
    p.curveTo(7.359375f, 23.186646f, 8.398166f, 24.368216f, 9.759766f, 24.597656f)
    p.lineTo(9.759766f, 27.519531f)
    p.lineTo(10.720703f, 27.519531f)
    p.lineTo(10.720703f, 24.597656f)
    p.curveTo(12.082303f, 24.368216f, 13.119141f, 23.186646f, 13.119141f, 21.759766f)
    p.curveTo(13.119141f, 20.332886f, 12.082302f, 19.152628f, 10.720703f, 18.923828f)
    p.lineTo(10.720703f, 13.076172f)
    p.curveTo(12.082303f, 12.847052f, 13.119141f, 11.666794f, 13.119141f, 10.240234f)
    p.curveTo(13.119141f, 8.813675f, 12.082302f, 7.631464f, 10.720703f, 7.402344f)
    p.lineTo(10.720703f, 4.480469f)
    p.lineTo(9.759766f, 4.4804688f)
    p.moveTo(13.121094f, 4.4804688f)
    p.curveTo(13.121094f, 5.9070287f, 14.158251f, 7.087286f, 15.519531f, 7.3164062f)
    p.lineTo(15.519531f, 13.1640625f)
    p.curveTo(14.157931f, 13.393183f, 13.119142f, 14.573441f, 13.119142f, 16.0f)
    p.curveTo(13.119142f, 17.42688f, 14.157931f, 18.608452f, 15.519531f, 18.83789f)
    p.lineTo(15.519531f, 24.683594f)
    p.curveTo(14.157931f, 24.913034f, 13.119142f, 26.09329f, 13.119142f, 27.519531f)
    p.lineTo(15.519531f, 27.519531f)
    p.lineTo(16.0f, 27.519531f)
    p.lineTo(16.480469f, 27.519531f)
    p.lineTo(18.88086f, 27.519531f)
    p.curveTo(18.88086f, 26.09233f, 17.84239f, 24.912394f, 16.480469f, 24.683594f)
    p.lineTo(16.480469f, 18.835938f)
    p.curveTo(17.84207f, 18.606497f, 18.88086f, 17.4272f, 18.88086f, 16.0f)
    p.curveTo(18.88086f, 14.57344f, 17.84207f, 13.391229f, 16.480469f, 13.162109f)
    p.lineTo(16.480469f, 7.3164062f)
    p.curveTo(17.842388f, 7.0879264f, 18.88086f, 5.9070287f, 18.88086f, 4.4804688f)
    p.lineTo(16.0f, 4.4804688f)
    p.lineTo(13.121094f, 4.4804688f)
    p.moveTo(21.279297f, 4.4804688f)
    p.lineTo(21.279297f, 7.4023438f)
    p.curveTo(19.917696f, 7.6314635f, 18.88086f, 8.813674f, 18.88086f, 10.240234f)
    p.curveTo(18.88086f, 11.666795f, 19.917698f, 12.847053f, 21.279297f, 13.076172f)
    p.lineTo(21.279297f, 18.923828f)
    p.curveTo(19.917696f, 19.152628f, 18.88086f, 20.332886f, 18.88086f, 21.759766f)
    p.curveTo(18.88086f, 23.186646f, 19.917698f, 24.368216f, 21.279297f, 24.597656f)
    p.lineTo(21.279297f, 27.519531f)
    p.lineTo(22.240234f, 27.519531f)
    p.lineTo(22.240234f, 24.597656f)
    p.curveTo(23.601835f, 24.368216f, 24.640625f, 23.186646f, 24.640625f, 21.759766f)
    p.curveTo(24.640625f, 20.332886f, 23.601833f, 19.152628f, 22.240234f, 18.923828f)
    p.lineTo(22.240234f, 13.076172f)
    p.curveTo(23.601835f, 12.847052f, 24.640625f, 11.666794f, 24.640625f, 10.240234f)
    p.curveTo(24.640625f, 8.813675f, 23.601833f, 7.631464f, 22.240234f, 7.402344f)
    p.lineTo(22.240234f, 4.480469f)
    p.lineTo(21.279297f, 4.4804688f)
    p.moveTo(24.640625f, 4.4804688f)
    p.curveTo(24.640625f, 5.9073486f, 25.677141f, 7.0876064f, 27.039062f, 7.3164062f)
    p.lineTo(27.039062f, 13.164062f)
    p.curveTo(25.677464f, 13.393182f, 24.640625f, 14.57344f, 24.640625f, 16.0f)
    p.curveTo(24.640625f, 17.42688f, 25.677462f, 18.608452f, 27.039062f, 18.83789f)
    p.lineTo(27.039062f, 24.683594f)
    p.curveTo(25.677143f, 24.912394f, 24.638672f, 26.092651f, 24.638672f, 27.519531f)
    p.lineTo(27.039062f, 27.519531f)
    p.lineTo(28.0f, 27.519531f)
    p.lineTo(28.0f, 4.4804688f)
    p.lineTo(27.519531f, 4.4804688f)
    p.lineTo(24.640625f, 4.4804688f)
  }

  def Stream(p: Path2D): Unit = {
    p.moveTo(15.673828f, 0.0f)
    p.lineTo(0.0f, 15.673828f)
    p.lineTo(0.32617188f, 16.0f)
    p.lineTo(0.6542969f, 16.326172f)
    p.lineTo(2.2851562f, 17.958984f)
    p.curveTo(3.2558424f, 16.988298f, 3.3528764f, 15.480582f, 2.5820312f, 14.398438f)
    p.lineTo(6.560547f, 10.419922f)
    p.curveTo(7.642909f, 11.190114f, 9.15019f, 11.091997f, 10.121094f, 10.121094f)
    p.curveTo(11.091561f, 9.150625f, 11.190331f, 7.6426907f, 10.419922f, 6.560547f)
    p.lineTo(14.3984375f, 2.5820312f)
    p.curveTo(15.480364f, 3.353094f, 16.988516f, 3.2575777f, 17.958984f, 2.2871094f)
    p.lineTo(16.0f, 0.32617188f)
    p.lineTo(15.673828f, 0.0f)
    p.moveTo(19.591797f, 3.9179688f)
    p.lineTo(17.603516f, 5.90625f)
    p.curveTo(16.521372f, 5.13584f, 15.011484f, 5.232657f, 14.041016f, 6.203125f)
    p.curveTo(13.070547f, 7.1735935f, 12.973731f, 8.683481f, 13.744141f, 9.765625f)
    p.lineTo(9.765625f, 13.744141f)
    p.curveTo(8.683699f, 12.973513f, 7.173811f, 13.07033f, 6.203125f, 14.041016f)
    p.curveTo(5.232439f, 15.011702f, 5.136058f, 16.521154f, 5.90625f, 17.603516f)
    p.lineTo(3.9179688f, 19.591797f)
    p.lineTo(4.5722656f, 20.246094f)
    p.lineTo(6.560547f, 18.257812f)
    p.curveTo(7.6429086f, 19.028006f, 9.150408f, 18.929672f, 10.121094f, 17.958984f)
    p.curveTo(11.09178f, 16.9883f, 11.190549f, 15.480364f, 10.419922f, 14.398438f)
    p.lineTo(14.3984375f, 10.419922f)
    p.curveTo(15.48058f, 11.190331f, 16.988516f, 11.091562f, 17.958984f, 10.121094f)
    p.curveTo(18.929453f, 9.150625f, 19.028223f, 7.6426907f, 18.257812f, 6.560547f)
    p.lineTo(20.246094f, 4.5722656f)
    p.lineTo(19.591797f, 3.9179688f)
    p.moveTo(21.878906f, 6.205078f)
    p.curveTo(20.908438f, 7.1755466f, 20.809883f, 8.683699f, 21.580078f, 9.765625f)
    p.lineTo(17.601562f, 13.744141f)
    p.curveTo(16.519419f, 12.973731f, 15.011484f, 13.070547f, 14.041017f, 14.041016f)
    p.curveTo(13.070331f, 15.011702f, 12.971995f, 16.521154f, 13.742188f, 17.603516f)
    p.lineTo(9.765625f, 21.580078f)
    p.curveTo(8.683263f, 20.809885f, 7.1733756f, 20.906702f, 6.203125f, 21.876953f)
    p.lineTo(7.8359375f, 23.509766f)
    p.lineTo(8.1640625f, 23.835938f)
    p.lineTo(8.490234f, 24.16406f)
    p.lineTo(10.123047f, 25.796873f)
    p.curveTo(11.093951f, 24.825968f, 11.190767f, 23.316515f, 10.419922f, 22.234373f)
    p.lineTo(14.3984375f, 18.255857f)
    p.curveTo(15.4808f, 19.026049f, 16.988081f, 18.929886f, 17.958984f, 17.958982f)
    p.curveTo(18.929453f, 16.988514f, 19.028223f, 15.478627f, 18.257812f, 14.396482f)
    p.lineTo(22.234375f, 10.41992f)
    p.curveTo(23.316303f, 11.190983f, 24.826406f, 11.093513f, 25.796875f, 10.123045f)
    p.lineTo(23.835938f, 8.1640625f)
    p.lineTo(21.878906f, 6.205078f)
    p.moveTo(27.427734f, 11.753906f)
    p.lineTo(25.439453f, 13.742188f)
    p.curveTo(24.35731f, 12.971779f, 22.849375f, 13.070548f, 21.878906f, 14.041017f)
    p.curveTo(20.908438f, 15.011484f, 20.809666f, 16.51942f, 21.580078f, 17.601562f)
    p.lineTo(17.601562f, 21.580078f)
    p.curveTo(16.519636f, 20.809448f, 15.011703f, 20.908222f, 14.041017f, 21.878906f)
    p.curveTo(13.070331f, 22.849594f, 12.971995f, 24.357092f, 13.742188f, 25.439453f)
    p.lineTo(11.753906f, 27.427734f)
    p.lineTo(12.408203f, 28.082031f)
    p.lineTo(14.396484f, 26.09375f)
    p.curveTo(15.478847f, 26.863941f, 16.988298f, 26.76756f, 17.958984f, 25.796875f)
    p.curveTo(18.92967f, 24.826189f, 19.026487f, 23.316301f, 18.25586f, 22.234375f)
    p.lineTo(22.234375f, 18.25586f)
    p.curveTo(23.316519f, 19.02627f, 24.826406f, 18.929453f, 25.796875f, 17.958984f)
    p.curveTo(26.767344f, 16.988516f, 26.864159f, 15.478628f, 26.09375f, 14.396484f)
    p.lineTo(28.082031f, 12.408203f)
    p.lineTo(27.427734f, 11.753906f)
    p.moveTo(29.714844f, 14.041017f)
    p.curveTo(28.744156f, 15.011703f, 28.647123f, 16.51942f, 29.417969f, 17.601562f)
    p.lineTo(25.439453f, 21.580078f)
    p.curveTo(24.35731f, 20.809668f, 22.849375f, 20.908438f, 21.878906f, 21.878906f)
    p.curveTo(20.908222f, 22.849594f, 20.809887f, 24.357092f, 21.580078f, 25.439453f)
    p.lineTo(17.601562f, 29.417969f)
    p.curveTo(16.519419f, 28.647123f, 15.011703f, 28.742207f, 14.041017f, 29.71289f)
    p.lineTo(15.673828f, 31.345703f)
    p.lineTo(16.326172f, 32.0f)
    p.lineTo(32.0f, 16.326172f)
    p.lineTo(31.673828f, 16.0f)
    p.lineTo(29.714844f, 14.041017f)
  }

  /////////////////////////////////////////////////////////////
  // the following shapes are taken from the Entypo+ project
  // http://www.entypo.com/
  //
  // Creative Commons CC BY-SA 4.0
  //
  // Copyright (c) Daniel Bruce

  def Gauge(p: Path2D): Unit = {
    p.moveTo(26.013672f, 7.544922f)
    p.curveTo(25.407272f, 7.194522f, 14.107906f, 21.766087f, 13.003906f, 23.679688f)
    p.curveTo(11.901506f, 25.594887f, 12.642629f, 27.16758f, 14.173828f, 28.050781f)
    p.curveTo(15.703428f, 28.935581f, 17.439322f, 28.794106f, 18.544922f, 26.878906f)
    p.curveTo(19.647322f, 24.966906f, 26.620071f, 7.895323f, 26.013672f, 7.544922f)
    p.lineTo(26.013672f, 7.544922f)
    p.moveTo(16.0f, 8.3203125f)
    p.curveTo(7.0288f, 8.3203125f, 0.0f, 15.885722f, 0.0f, 25.544922f)
    p.curveTo(0.0f, 26.138521f, 0.025325f, 26.732946f, 0.078125f, 27.310547f)
    p.curveTo(0.156525f, 28.192146f, 0.9421f, 28.836918f, 1.8125f, 28.761719f)
    p.curveTo(2.6941f, 28.680119f, 3.3440251f, 27.90699f, 3.265625f, 27.02539f)
    p.curveTo(3.222425f, 26.54219f, 3.199219f, 26.040922f, 3.199219f, 25.544922f)
    p.curveTo(3.199219f, 17.679321f, 8.8224f, 11.519531f, 16.0f, 11.519531f)
    p.curveTo(16.7008f, 11.519531f, 17.381628f, 11.578161f, 18.048828f, 11.693359f)
    p.curveTo(18.749628f, 10.81496f, 19.533674f, 9.848088f, 20.296875f, 8.9296875f)
    p.curveTo(18.933676f, 8.536088f, 17.4944f, 8.3203125f, 16.0f, 8.3203125f)
    p.lineTo(16.0f, 8.3203125f)
    p.moveTo(27.550781f, 13.519531f)
    p.curveTo(27.104382f, 14.719531f, 26.632797f, 15.942047f, 26.216797f, 16.998047f)
    p.curveTo(27.840797f, 19.350046f, 28.800781f, 22.300121f, 28.800781f, 25.544922f)
    p.curveTo(28.800781f, 26.050522f, 28.77527f, 26.561888f, 28.73047f, 27.054688f)
    p.curveTo(28.65047f, 27.936287f, 29.29969f, 28.713322f, 30.17969f, 28.794922f)
    p.curveTo(30.22769f, 28.798122f, 30.27657f, 28.800821f, 30.326174f, 28.800821f)
    p.curveTo(31.143774f, 28.800821f, 31.842772f, 28.176144f, 31.91797f, 27.345743f)
    p.curveTo(31.97077f, 26.753742f, 32.0f, 26.148163f, 32.0f, 25.544962f)
    p.curveTo(32.0f, 20.818562f, 30.31558f, 16.60117f, 27.550781f, 13.519571f)
    p.lineTo(27.550781f, 13.519531f)
  }

  /////////////////////////////////////////////////////////////
  // the following shapes are adapted from the Noun project
  // https://thenounproject.com/term/transfer/370305/
  //
  // Creative Commons CC BY 3.0 US
  //
  // Copyright (c)  Gonzalo Bravo

  def Transfer(p: Path2D): Unit = {
    p.moveTo(10.5f, 3f)
    p.lineTo(1.0f, 12.5f)
    p.lineTo(10.5f, 22.0f)
    p.lineTo(10.5f, 17f)
    p.lineTo(23.5f, 17.0f)
    p.lineTo(23.5f, 15.0f)
    p.lineTo(28.0f, 19.5f)
    p.lineTo(23.5f, 24.0f)
    p.lineTo(23.5f, 22.0f)
    p.lineTo(16.0f, 22.0f)
    p.lineTo(16.0f, 24.0f)
    p.lineTo(21.5f, 24.0f)
    p.lineTo(21.5f, 29.0f)
    p.lineTo(31.0f, 19.5f)
    p.lineTo(21.5f, 10.0f)
    p.lineTo(21.5f, 15.0f)
    p.lineTo(8.5f, 15.0f)
    p.lineTo(8.5f, 17.0f)
    p.lineTo(4.0f, 12.5f)
    p.lineTo(8.5f, 8.0f)
    p.lineTo(8.5f, 10f)
    p.lineTo(16f, 10.0f)
    p.lineTo(16f, 8.0f)
    p.lineTo(10.5f, 8.0f)
    p.lineTo(10.5f, 3f)
  }

  /////////////////////////////////////////////////////////////
  // the following shapes are adapted from the Noun project
  // https://thenounproject.com/term/timeline/370305/
  //
  // Creative Commons CC BY 3.0 US
  //
  // Copyright (c)   Alex Bickov

  def Timeline(p: Path2D): Unit = {
    p.moveTo(16.04096f, 28.65536f)
    p.lineTo(16.04096f, 21.3952f)
    p.lineTo(30.4f, 21.3952f)
    p.lineTo(30.4f, 28.655682f)
    p.lineTo(16.04096f, 28.655682f)
    p.lineTo(16.04096f, 28.65536f)
    p.moveTo(1.6f, 3.34464f)
    p.lineTo(30.4f, 3.34464f)
    p.lineTo(30.4f, 10.6048f)
    p.lineTo(1.6f, 10.6048f)
    p.lineTo(1.6f, 3.34464f)
    p.moveTo(23.17952f, 19.6304f)
    p.lineTo(8.82048f, 19.6304f)
    p.lineTo(8.82048f, 12.3696f)
    p.lineTo(23.17952f, 12.3696f)
    p.lineTo(23.17952f, 19.6304f)
  }
}