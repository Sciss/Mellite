/*
 *  CurveCmdLine.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.proc.TimeRef
import de.sciss.synth.Curve
import org.rogach.scallop

import scala.util.Try

object CmdLineSupport {
  private val curveNameMap: Map[String, Curve] = Map(
    "step"        -> Curve.step,
    "lin"         -> Curve.linear,
    "linear"      -> Curve.linear,
    "exp"         -> Curve.exponential,
    "exponential" -> Curve.exponential,
    "sin"         -> Curve.sine,
    "sine"        -> Curve.sine,
    "welch"       -> Curve.welch,
    "sqr"         -> Curve.squared,
    "squared"     -> Curve.squared,
    "cub"         -> Curve.cubed,
    "cubed"       -> Curve.cubed
  )

//  private val durSpan       = Span.From(0L)
//  private val fmtDurTime    = new TimeField.TimeFormat  (durSpan, sampleRate = TimeRef.SampleRate)
//  private val fmtDurFrames  = new TimeField.FramesFormat(durSpan, sampleRate = TimeRef.SampleRate,
//    viewSampleRate = TimeRef.SampleRate)
//  private val fmtDurMilli   = new TimeField.MilliFormat (durSpan, sampleRate = TimeRef.SampleRate)

  def parseDuration(s: String): Option[Long] =
    Try {
      if (s.endsWith("ms")) {
        val sec = s.substring(0, s.length - 2).trim.toDouble * 0.001
        (sec * TimeRef.SampleRate + 0.5).toLong

      } else if (s.contains(".") || s.contains(":")) {
        val arr = s.split(':')
        val sec0 = arr(arr.length - 1).toDouble
        val min0 = if (arr.length <= 1) 0 else {
          arr(arr.length - 2).toInt
        }
        val hour = if (arr.length <= 2) 0 else {
          arr(arr.length - 3).toInt
        }
        val min1 = hour * 60 + min0
        val sec1 = min1 * 60.0
        val sec2 = sec0 + sec1
        (sec2 * TimeRef.SampleRate + 0.5).toLong

      } else { // frames
        s.toLong
      }
    } .toOption

  final case class Frames(value: Long)

  implicit val ReadDuration: scallop.ValueConverter[Frames] = scallop.singleArgConverter { s =>
    parseDuration(s) match {
      case Some(n)  => Frames(n)
      case None     => throw new IllegalArgumentException(s"Not a valid time format: $s")
    }
  }

  implicit val ReadCurve: scallop.ValueConverter[Curve] = scallop.singleArgConverter { s =>
    curveNameMap.getOrElse(s.toLowerCase, {
      val p = s.toFloat
      Curve.parametric(p)
    })
  }
}
