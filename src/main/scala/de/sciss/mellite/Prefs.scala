/*
 *  Prefs.scala
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

import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

import de.sciss.desktop.Preferences
import de.sciss.desktop.Preferences.{Entry, Type}
import de.sciss.file._
import de.sciss.osc
import de.sciss.submin.Submin
import de.sciss.synth.proc.SensorSystem

import scala.util.{Success, Try}

object Prefs {
  import Application.userPrefs

  implicit object OSCProtocolType extends Type[osc.Transport.Net] {
    def toString(value: osc.Transport.Net): String = value.name
    def valueOf(string: String): Option[osc.Transport.Net] =
      Try(osc.Transport(string)) match {
        case Success(net: osc.Transport.Net) => Some(net)
        case _ => None
      }
  }

  // ---- system ----

  final val defaultDbLockTimeout = 500
  def dbLockTimeout: Entry[Int] = userPrefs("lock-timeout")

  // ---- gui ----

  object LookAndFeel {
    implicit object Type extends Preferences.Type[LookAndFeel] {
      def toString(value: LookAndFeel): String = value.id
      def valueOf(string: String): Option[LookAndFeel] = all.find(_.id == string)
    }

    case object Native extends LookAndFeel {
      val id          = "native"
      val description = "Native"

      def install(): Unit = UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
    }

    case object Metal extends LookAndFeel {
      val id          = "metal"
      val description = "Metal"

      def install(): Unit = UIManager.setLookAndFeel(classOf[MetalLookAndFeel].getName)
    }

    case object Light extends LookAndFeel {
      val id          = "light"
      val description = "Submin Light"

      def install(): Unit = Submin.install(false)
    }

    case object Dark extends LookAndFeel {
      val id          = "dark"
      val description = "Submin Dark"

      def install(): Unit = Submin.install(true)
    }

    def all: Seq[LookAndFeel] = Seq(Native, Metal, Light, Dark)

    def default: LookAndFeel = Light
  }

  sealed trait LookAndFeel {
    def install(): Unit
    def id: String
    def description: String
  }

  def lookAndFeel: Entry[LookAndFeel] = userPrefs("look-and-feel")

  def nativeWindowDecoration: Entry[Boolean] = userPrefs("native-window-decoration")

  // ---- audio ----

  final val defaultSuperCollider: File  = file("<SC_HOME>")
  final val defaultAudioDevice          = "<default>"
  final val defaultAudioSampleRate      = 0

  final val defaultAudioBlockSize       = 64
  final val defaultAudioNumInputs       = 2
  final val defaultAudioNumOutputs      = 2
  final val defaultAudioNumPrivate      = 512
  final val defaultAudioNumWireBufs     = 256
  final val defaultAudioNumAudioBufs    = 1024
  final val defaultAudioMemorySize      = 64

  final val defaultHeadphonesBus        = 0
  final val defaultAudioLatency         = 200

  def superCollider     : Entry[File   ] = userPrefs("supercollider"        )
  def audioDevice       : Entry[String ] = userPrefs("audio-device"         )
  def audioNumInputs    : Entry[Int    ] = userPrefs("audio-num-inputs"     )
  def audioNumOutputs   : Entry[Int    ] = userPrefs("audio-num-outputs"    )
  def audioSampleRate   : Entry[Int    ] = userPrefs("audio-sample-rate"    )

  def audioBlockSize    : Entry[Int    ] = userPrefs("audio-block-size"     )
  def audioNumPrivate   : Entry[Int    ] = userPrefs("audio-num-private"    )
  def audioNumAudioBufs : Entry[Int    ] = userPrefs("audio-num-audio-bufs" )
  def audioNumWireBufs  : Entry[Int    ] = userPrefs("audio-num-wire-bufs"  )

  /** In megabytes */
  def audioMemorySize   : Entry[Int    ] = userPrefs("audio-memory-size"    )

  def headphonesBus     : Entry[Int    ] = userPrefs("headphones-bus"       )
  def audioAutoBoot     : Entry[Boolean] = userPrefs("audio-auto-boot"      )

  /** In milliseconds */
  def audioLatency      : Entry[Int    ] = userPrefs("audio-latency"        )

  // ---- sensor ----

  final val defaultSensorProtocol: osc.Transport.Net = osc.UDP
  def defaultSensorPort   : Int     = SensorSystem.defaultPort // 0x4D6C  // "Ml"
  def defaultSensorCommand: String  = SensorSystem.defaultCommand
  final val defaultSensorChannels   = 0

  def sensorProtocol : Entry[osc.Transport.Net] = userPrefs("sensor-protocol")
  def sensorPort     : Entry[Int              ] = userPrefs("sensor-port"    )
  def sensorCommand  : Entry[String           ] = userPrefs("sensor-command" )
  def sensorChannels : Entry[Int              ] = userPrefs("sensor-channels")
  def sensorAutoStart: Entry[Boolean          ] = userPrefs("sensor-auto-start")

  // ---- sub-applications ----
  // they are here, because right now Mellite is `DelayedInit` which
  // can cause trouble with `var`s.

  /** The master volume in decibels. A value of -72 or less
    * is mapped to -inf.
    */
  def audioMasterVolume   : Entry[Int] = userPrefs("audio-master-volume")
  def headphonesVolume    : Entry[Int] = userPrefs("headphones-volume")

  def audioMasterLimiter  : Entry[Boolean]  = userPrefs("audio-master-limiter")
  def audioMasterPostMeter: Entry[Boolean]  = userPrefs("audio-master-post-meter")
  def headphonesActive    : Entry[Boolean]  = userPrefs("headphones-active")

  /** Whether to create a log (post) window or not. Defaults to `true`. */
  var useLogFrame: Boolean = true

  /** Whether to create a bus meters for the audio server or not. Defaults to `true`. */
  var useAudioMeters: Boolean = true

  /** Whether to create a meters for the sensors or not. Defaults to `true`. */
  var useSensorMeters: Boolean = true
}