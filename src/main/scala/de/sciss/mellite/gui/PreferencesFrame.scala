/*
 *  PreferencesFrame.scala
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

import de.sciss.desktop.KeyStrokes.menu1
import de.sciss.desktop.{Desktop, PrefsGUI, Window, WindowHandler}
import de.sciss.file._
import de.sciss.swingplus.{GroupPanel, Separator}
import de.sciss.{desktop, equal, osc}
import javax.swing.JComponent

import scala.swing.{Action, Component}
import scala.swing.event.Key

final class PreferencesFrame extends desktop.impl.WindowImpl {

  def handler: WindowHandler = Application.windowHandler

  override protected def style: Window.Style = Window.Auxiliary

  private[this] val box: Component = {
    import PrefsGUI._

    // ---- appearance ----

    val lbLookAndFeel   = label("Look-and-Feel")
    val ggLookAndFeel   = combo(Prefs.lookAndFeel, Prefs.LookAndFeel.default,
      Prefs.LookAndFeel.all)(_.description)

    val lbNativeDecoration = label("Native Window Decoration")
    val ggNativeDecoration = checkBox(Prefs.nativeWindowDecoration, default = true)

    // ---- audio ----
    val sepAudio = Separator()

    val lbSuperCollider = label("SuperCollider (scsynth)")
    val ggSuperCollider = pathField(Prefs.superCollider, Prefs.defaultSuperCollider,
      title = "SuperCollider Server Location (scsynth)", accept = { f =>
        import equal.Implicits._
        val f2 = if (Desktop.isMac && f.ext === "app") {
          val f1 = f / "Contents" / "Resources" / "scsynth"
          if (f1.exists) f1 else f
        } else f
        Some(f2)
      })

    val lbAudioAutoBoot = label("Automatic Boot")
    val ggAudioAutoBoot = checkBox(Prefs.audioAutoBoot, default = false)

    val lbAudioDevice   = label("Audio Device")
    val ggAudioDevice   = textField(Prefs.audioDevice    , Prefs.defaultAudioDevice     )
    val lbNumInputs     = label("Input Channels")
    val ggNumInputs     = intField(Prefs.audioNumInputs  , Prefs.defaultAudioNumInputs  )
    val lbNumOutputs    = label("Output Channels")
    val ggNumOutputs    = intField(Prefs.audioNumOutputs , Prefs.defaultAudioNumOutputs )
    val lbSampleRate    = label("Sample Rate")
    val ggSampleRate    = intField(Prefs.audioSampleRate , Prefs.defaultAudioSampleRate, max = 384000)

    val sepAudioAdvanced = Separator()

    val lbBlockSize     = label("Block Size")
    val ggBlockSize     = intField(Prefs.audioBlockSize  , Prefs.defaultAudioBlockSize, min = 1)
    val lbNumPrivate    = label("Private Channels")
    val ggNumPrivate    = intField(Prefs.audioNumPrivate , Prefs.defaultAudioNumPrivate, min = 4)
    val lbNumAudioBufs  = label("Audio Buffers")
    val ggNumAudioBufs  = intField(Prefs.audioNumAudioBufs, Prefs.defaultAudioNumAudioBufs, min = 4)
    val lbNumWireBufs   = label("Wire Buffers")
    val ggNumWireBufs   = intField(Prefs.audioNumWireBufs, Prefs.defaultAudioNumWireBufs, min = 4, max = 262144)
    val lbMemorySize    = label("Real-Time Memory [MB]")
    val ggMemorySize    = intField(Prefs.audioMemorySize , Prefs.defaultAudioMemorySize , min = 1, max = 8388608)
    val lbLatency       = label("OSC Latency [ms]")
    val ggLatency       = intField(Prefs.audioLatency, Prefs.defaultAudioLatency, min = 0, max = 10000)

    val sepAudioHeadphones = Separator()

    val lbHeadphones    = label("Headphones Bus")
    val ggHeadphones    = intField(Prefs.headphonesBus   , Prefs.defaultHeadphonesBus   )

    // ---- sensor ----
    val sepSensor = Separator()

    val lbSensorProtocol  = label("Sensor Protocol")
    val ggSensorProtocol  = combo(Prefs.sensorProtocol, Prefs.defaultSensorProtocol, Seq(osc.UDP, osc.TCP))(_.name)

    val lbSensorPort      = label("Sensor Port")
    val ggSensorPort      = intField(Prefs.sensorPort, Prefs.defaultSensorPort)

    val lbSensorAutoStart = label("Automatic Start")
    val ggSensorAutoStart = checkBox(Prefs.sensorAutoStart, default = false)

    val lbSensorCommand   = label("Sensor Command")
    val ggSensorCommand   = textField(Prefs.sensorCommand, Prefs.defaultSensorCommand)

    val lbSensorChannels  = label("Sensor Channels")
    val ggSensorChannels  = intField(Prefs.sensorChannels, Prefs.defaultSensorChannels)

    // ---- system ----
    val sepDatabase = Separator()

    val lbLockTimeout   = label("Database Lock Timeout [ms]")
    val ggLockTimeout   = intField(Prefs.dbLockTimeout, Prefs.defaultDbLockTimeout)

    // ---- panel ----

    val _box = new GroupPanel {
      // val lbValue = new Label("Value:", EmptyIcon, Alignment.Right)
      horizontal = Par(sepAudio, sepSensor, sepAudioAdvanced, sepAudioHeadphones, sepDatabase, Seq(
        Par(lbLookAndFeel, lbNativeDecoration, lbSuperCollider, lbAudioAutoBoot, lbAudioDevice, lbNumInputs, lbNumOutputs,
          lbSampleRate, lbBlockSize, lbNumPrivate, lbNumAudioBufs, lbNumWireBufs, lbMemorySize, lbLatency, lbHeadphones, lbSensorProtocol, lbSensorPort,
          lbSensorAutoStart, lbSensorCommand, lbSensorChannels, lbLockTimeout),
        Par(ggLookAndFeel, ggNativeDecoration, ggSuperCollider, ggAudioAutoBoot, ggAudioDevice, ggNumInputs, ggNumOutputs,
          ggSampleRate, ggNumPrivate, ggBlockSize, ggNumAudioBufs, ggNumWireBufs, ggMemorySize, ggLatency, ggHeadphones, ggSensorProtocol, ggSensorPort,
          ggSensorAutoStart, ggSensorCommand, ggSensorChannels, ggLockTimeout)
      ))
      vertical = Seq(
        Par(Baseline)(lbLookAndFeel     , ggLookAndFeel     ),
        Par(Baseline)(lbNativeDecoration, ggNativeDecoration),
        sepAudio,
        Par(Baseline)(lbSuperCollider   , ggSuperCollider   ),
        Par(Baseline)(lbAudioAutoBoot   , ggAudioAutoBoot   ),
        Par(Baseline)(lbAudioDevice     , ggAudioDevice     ),
        Par(Baseline)(lbNumInputs       , ggNumInputs       ),
        Par(Baseline)(lbNumOutputs      , ggNumOutputs      ),
        Par(Baseline)(lbSampleRate      , ggSampleRate      ),
        sepAudioAdvanced,
        Par(Baseline)(lbBlockSize       , ggBlockSize       ),
        Par(Baseline)(lbNumPrivate      , ggNumPrivate      ),
        Par(Baseline)(lbNumAudioBufs    , ggNumAudioBufs    ),
        Par(Baseline)(lbNumWireBufs     , ggNumWireBufs     ),
        Par(Baseline)(lbMemorySize      , ggMemorySize      ),
        Par(Baseline)(lbLatency         , ggLatency         ),
        sepAudioHeadphones,
        Par(Baseline)(lbHeadphones      , ggHeadphones      ),
        sepSensor,
        Par(Baseline)(lbSensorProtocol  , ggSensorProtocol  ),
        Par(Baseline)(lbSensorPort      , ggSensorPort      ),
        Par(Baseline)(lbSensorAutoStart , ggSensorAutoStart ),
        Par(Baseline)(lbSensorCommand   , ggSensorCommand   ),
        Par(Baseline)(lbSensorChannels  , ggSensorChannels  ),
        sepDatabase,
        Par(Baseline)(lbLockTimeout     , ggLockTimeout     )
      )
    }

    _box
  }

  contents = box

  {
    // XXX TODO --- there should be a general mechanism for this
    val actionClose = Action(null)(dispose())
    val am          = box.peer.getActionMap
    val im          = box.peer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    am.put("file.close", actionClose.peer)
    im.put(menu1 + Key.W, "file.close")
  }

  title           = "Preferences"
  closeOperation  = Window.CloseDispose
  pack()
  desktop.Util.centerOnScreen(this)
  front()
}
