/*
 *  Prefs.scala
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

import java.io.File

import de.sciss.desktop.Preferences
import de.sciss.desktop.Preferences.{Entry, Type}
import de.sciss.file._
import de.sciss.mellite.Application.userPrefs
import de.sciss.osc
import de.sciss.proc.SensorSystem
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

import scala.util.{Success, Try}

object Prefs {

  implicit object OSCProtocolType extends Type[osc.Transport] {
    def toString(value: osc.Transport): String = value.name
    def valueOf(string: String): Option[osc.Transport] =
      Try(osc.Transport(string)) match {
        case Success(net: osc.Transport) => Some(net)
        case _ => None
      }
  }

  // ---- system ----

  final val defaultDbLockTimeout = 500

  def dbLockTimeout: Entry[Int] = userPrefs("lock-timeout")

  /** Is interpreted as using the JVM's default `java.io.tmpdir` */
  final val defaultTempDir: File  = file("<default>")

  /** Temporary directory; is set at application start, using system property `java.io.tmpdir`. */
  def tempDir: Entry[File] = userPrefs("temp-dir")

  /** Unit: days. Zero means disabled */
  def updateCheckPeriod: Entry[Int] = userPrefs("update-check-period")

  final val defaultUpdateCheckPeriod: Int = 7

  // ---- appearance ----

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

    private def installSubmin(dark: Boolean): Unit = {
      val cSubmin  = Class.forName("de.sciss.submin.Submin")
      val mInstall = cSubmin.getMethod("install", classOf[Boolean])
      mInstall.invoke(null, dark.asInstanceOf[AnyRef])
    }

    case object Light extends LookAndFeel {
      val id          = "light"
      val description = "Submin Light"

      def install(): Unit = installSubmin(false) // Submin.install(false)
    }

    case object Dark extends LookAndFeel {
      val id          = "dark"
      val description = "Submin Dark"

      def install(): Unit = installSubmin(true) // Submin.install(true)
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

  final val defaultNativeWindowDecoration = true

  def nativeWindowDecoration: Entry[Boolean] = userPrefs("native-window-decoration")

  final val defaultScreenMenuBar = false

  /** Only relevant on macOS. */
  def screenMenuBar: Entry[Boolean] = userPrefs("screen-menu-bar")

  final val defaultCodeFontFamily = "DejaVu Sans Mono"

  def codeFontFamily: Entry[String] = userPrefs("code-font-family")

  final val defaultCodeFontSize = 13

  def codeFontSize: Entry[Int] = userPrefs("code-font-size")

  final val defaultCodeFontStretch = 100

  /** In percent */
  def codeFontStretch: Entry[Int] = userPrefs("code-font-stretch")

  final val defaultCodeLineSpacing = 112

  /** In percent */
  def codeLineSpacing: Entry[Int] = userPrefs("code-line-spacing")

  //  def defaultRevealFileCmd: String = {
//    if (Desktop.isLinux) {
//      val dirs = sys.env.getOrElse("PATH", "").split(File.pathSeparator)
//      // note: Raspberry will also have `pcmanfm` but it doesn't allow to select a file
//      // and thus we can simply fall back to `xdg-open` with the parent directory.
//      dirs.collectFirst {
//        case dir if new File(dir, "nautilus").exists() => "nautilus %p"
//      } .getOrElse("xdg-open %d")
//
//    } else if (Desktop.isMac) {
//      "open -R %p"
//
//    } else {
//      "explorer.exe /select,%p"
//    }
//  }
//
//  /** Placeholders: %p path, %d parent directory %f file-name; upper case for URLs. */
//  def revealFileCmd: Entry[String] = userPrefs("reveal-file")

  def viewSaveState: Entry[Boolean] = userPrefs("view-save-state")

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

  final val defaultSensorProtocol: osc.Transport = osc.UDP
  def defaultSensorPort   : Int     = SensorSystem.defaultPort // 0x4D6C  // "Ml"
  def defaultSensorCommand: String  = SensorSystem.defaultCommand
  final val defaultSensorChannels   = 0

  def sensorProtocol : Entry[osc.Transport] = userPrefs("sensor-protocol")
  def sensorPort     : Entry[Int          ] = userPrefs("sensor-port"    )
  def sensorCommand  : Entry[String       ] = userPrefs("sensor-command" )
  def sensorChannels : Entry[Int          ] = userPrefs("sensor-channels")
  def sensorAutoStart: Entry[Boolean      ] = userPrefs("sensor-auto-start")

  // ---- audio mixer ----

  /** The main volume in decibels. A value of -72 or less
    * is mapped to -inf.
    */
  def audioMainVolume     : Entry[Int] = userPrefs("audio-main-volume")
  def headphonesVolume    : Entry[Int] = userPrefs("headphones-volume")

  def audioMainLimiter    : Entry[Boolean]  = userPrefs("audio-main-limiter")
  def audioMainPostMeter  : Entry[Boolean]  = userPrefs("audio-main-post-meter")
  def headphonesActive    : Entry[Boolean]  = userPrefs("headphones-active")

  // ---- applications can set these ----

  /** Whether to create a log (post) window or not. Defaults to `true`. */
  var useLogFrame: Boolean = true

  /** Whether to create a bus meters for the audio server or not. Defaults to `true`. */
  var useAudioMeters: Boolean = true

  /** Whether to create a meters for the sensors or not. Defaults to `true`. */
  var useSensorMeters: Boolean = true
}