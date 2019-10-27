/*
 *  PreferencesFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.desktop.{Desktop, Preferences, PrefsGUI, Window, WindowHandler}
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.mellite.impl.component.NoMenuBarActions
import de.sciss.swingplus.GroupPanel.Element
import de.sciss.swingplus.{GroupPanel, Separator}
import de.sciss.{desktop, equal, osc}

import scala.swing.{Action, Alignment, Component, Label, TabbedPane}

final class PreferencesFrame extends desktop.impl.WindowImpl with NoMenuBarActions {

  def handler: WindowHandler = Application.windowHandler

  override protected def style: Window.Style = Window.Auxiliary

  protected def handleClose(): Unit = dispose()

  protected def undoRedoActions: Option[(Action, Action)] = None

  private[this] val box: Component = {
    import PrefsGUI._

    def comboL[A](prefs: Preferences.Entry[A], default: => A, values: Seq[A])
                 (implicit view: A => String): Component = {
      val c = combo(prefs, default, values)
      c.maximumSize = c.preferredSize
      c
    }

    // ---- appearance ----

    val lbLookAndFeel   = label("Look-and-Feel")
    val ggLookAndFeel   = comboL(Prefs.lookAndFeel, Prefs.LookAndFeel.default,
      Prefs.LookAndFeel.all)(_.description)

    val lbNativeDecoration  = label("Native Window Decoration")
    val ggNativeDecoration  = checkBox(Prefs.nativeWindowDecoration, default = true)
    val icnWarn             = GUI.iconNormal(raphael.Shapes.Warning)
    val lbWarnAppIcon       = new Label(null, icnWarn, Alignment.Trailing)
    val lbWarnAppText       = new Label(
      "<html><body>Changes to appearance take only effect<br>after restarting the application.",
      null, Alignment.Leading)

//    val lbRevealFileCmd = label("Reveal File Command")
//    val ggRevealFileCmd = textField(Prefs.revealFileCmd, Prefs.defaultRevealFileCmd)
//    ggRevealFileCmd.tooltip = "Use placeholders %p (full path), %d (directory) %f (file name)"

    // ---- audio ----

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

    val lbBlockSize     = label("Block Size")
    val ggBlockSize     = intField(Prefs.audioBlockSize  , Prefs.defaultAudioBlockSize, min = 1)
    val lbNumPrivate    = label("Private Channels")
    val ggNumPrivate    = intField(Prefs.audioNumPrivate , Prefs.defaultAudioNumPrivate, min = 4)
    val lbNumAudioBufs  = label("Audio Buffers")
    val ggNumAudioBufs  = intField(Prefs.audioNumAudioBufs, Prefs.defaultAudioNumAudioBufs, min = 4)
    val lbNumWireBufs   = label("Wire Buffers")
    val ggNumWireBufs   = intField(Prefs.audioNumWireBufs, Prefs.defaultAudioNumWireBufs, min = 4, max = 262144)
    val lbMemorySize    = label("Real-Time Memory [MiB]")
    val ggMemorySize    = intField(Prefs.audioMemorySize , Prefs.defaultAudioMemorySize , min = 1, max = 8388608)
    val lbLatency       = label("OSC Latency [ms]")
    val ggLatency       = intField(Prefs.audioLatency, Prefs.defaultAudioLatency, min = 0, max = 10000)

    val lbHeadphones    = label("Headphones Bus")
    val ggHeadphones    = intField(Prefs.headphonesBus   , Prefs.defaultHeadphonesBus   )

    // ---- sensors ----

    val lbSensorProtocol  = label("Sensor Protocol")
    val ggSensorProtocol  = comboL(Prefs.sensorProtocol, Prefs.defaultSensorProtocol, Seq(osc.UDP, osc.TCP))(_.name)

    val lbSensorPort      = label("Sensor Port")
    val ggSensorPort      = intField(Prefs.sensorPort, Prefs.defaultSensorPort)

    val lbSensorAutoStart = label("Automatic Start")
    val ggSensorAutoStart = checkBox(Prefs.sensorAutoStart, default = false)

    val lbSensorCommand   = label("Sensor Command")
    val ggSensorCommand   = textField(Prefs.sensorCommand, Prefs.defaultSensorCommand)

    val lbSensorChannels  = label("Sensor Channels")
    val ggSensorChannels  = intField(Prefs.sensorChannels, Prefs.defaultSensorChannels)

    // ---- system ----

    val lbLockTimeout   = label("Database Lock Timeout [ms]")
    val ggLockTimeout   = intField(Prefs.dbLockTimeout, Prefs.defaultDbLockTimeout)

    // ---- panel ----

    val tabbed = new TabbedPane
    tabbed.peer.putClientProperty("styleId", "attached")  // XXX TODO: obsolete

//    def interleave[A](a: List[A], b: List[A]): List[A] = {
//      val aIt = a.iterator
//      val bIt = b.iterator
//      val res = List.newBuilder[A]
//      while (aIt.hasNext) {
//        res += aIt.next()
//        if (bIt.hasNext) res += bIt.next()
//      }
//      res.result()
//    }

    def mkPage(name: String)(entries: List[(Component, Component)]*): Unit = {
      val sepPar    = List.newBuilder[Element.Par]
      val labelsPar = List.newBuilder[Element.Par]
      val fieldsPar = List.newBuilder[Element.Par]
      val collSeq   = List.newBuilder[Element.Seq]

      val _box = new GroupPanel {
        {
          val eIt = entries.iterator
          while (eIt.hasNext) {
            val pairs = eIt.next()
            pairs.foreach { case (lb, f) =>
              labelsPar += lb
              fieldsPar += f
              collSeq   += Par(Baseline)(lb, f)
            }
            if (eIt.hasNext) {
              val sep    = Separator()
              sepPar    += sep
              collSeq   += sep
            }
          }
        }

        horizontal  = Par(sepPar.result() :+ Seq(
          Par(GroupPanel.Alignment.Trailing)(labelsPar.result(): _*),
          Par(fieldsPar.result(): _*)): _*
        )
        vertical    = Seq(collSeq.result(): _*)
      }
      val page = new TabbedPane.Page(name, _box, null) // cf. https://github.com/scala/scala-swing/issues/105
      tabbed.pages += page
    }

    mkPage("Appearance")(
      List(
        (lbLookAndFeel      , ggLookAndFeel     ),
        (lbNativeDecoration , ggNativeDecoration),
      ),
      List(
        (lbWarnAppIcon, lbWarnAppText)
//      ),
//      List(
//        (lbRevealFileCmd    , ggRevealFileCmd   )
      )
    )
    mkPage("Audio")(
      List(
        (lbSuperCollider    , ggSuperCollider   ),
        (lbAudioAutoBoot    , ggAudioAutoBoot   ),
        (lbAudioDevice      , ggAudioDevice     ),
        (lbNumInputs        , ggNumInputs       ),
        (lbNumOutputs       , ggNumOutputs      ),
        (lbSampleRate       , ggSampleRate      )
      ),
      List(
        (lbBlockSize        , ggBlockSize       ),
        (lbNumPrivate       , ggNumPrivate      ),
        (lbNumAudioBufs     , ggNumAudioBufs    ),
        (lbNumWireBufs      , ggNumWireBufs     ),
        (lbMemorySize       , ggMemorySize      ),
        (lbLatency          , ggLatency         )
      ),
      List(
        (lbHeadphones       , ggHeadphones      )
      )
    )
    mkPage("Sensors")(
      List(
        (lbSensorProtocol   , ggSensorProtocol  ),
        (lbSensorPort       , ggSensorPort      ),
        (lbSensorAutoStart  , ggSensorAutoStart ),
        (lbSensorCommand    , ggSensorCommand   ),
        (lbSensorChannels   , ggSensorChannels  )
      )
    )
    mkPage("System")(
      List(
        (lbLockTimeout      , ggLockTimeout     )
      )
    )

    tabbed
  }

  contents = box

  initNoMenuBarActions(box)

  title           = "Preferences"
  closeOperation  = Window.CloseDispose
  pack()
  desktop.Util.centerOnScreen(this)
  front()
}
