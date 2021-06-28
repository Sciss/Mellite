/*
 *  PreferencesFrame.scala
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

import de.sciss.desktop.{Desktop, FileDialog, Preferences, PrefsGUI, Window, WindowHandler}
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.mellite.PreferencesFrame.Tab
import de.sciss.mellite.impl.component.{BaselineFlowPanel, NoMenuBarActions}
import de.sciss.swingplus.GroupPanel.Element
import de.sciss.swingplus.{GroupPanel, Separator}
import de.sciss.{desktop, equal, osc}
import net.harawata.appdirs.AppDirsFactory

import java.io.{FileInputStream, FileOutputStream}
import java.util.{Properties => JProperties}
import scala.swing.{Action, Alignment, Button, Component, FlowPanel, Label, Swing, TabbedPane}
import scala.util.Try
import scala.util.control.NonFatal

object PreferencesFrame {
  object Tab extends Enumeration {
    val Appearance, Audio, Sensors, System = Value
    val Default: Value = Appearance
  }
}
final class PreferencesFrame(selectedTab: Tab.Value) extends desktop.impl.WindowImpl with NoMenuBarActions {
  def handler: WindowHandler = Application.windowHandler

  def this() = this(Tab.Default)

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
    val ggNativeDecoration  = checkBox(Prefs.nativeWindowDecoration, default = Prefs.defaultNativeWindowDecoration)

    val lbScreenMenuBar     = label("Screen Menu Bar")
    val ggScreenMenuBar     = checkBox(Prefs.screenMenuBar, default = Prefs.defaultScreenMenuBar)
    if (!Desktop.isMac) {
      lbScreenMenuBar.visible = false
      ggScreenMenuBar.visible = false
    }

    def mkFlow(hGap0: Int, components: Component*): Component = {
      val fp = new BaselineFlowPanel(components: _*)
      fp.hGap = hGap0
      fp
    }

    val lbCodeFont          = label("Code Font")
    val ggCodeFontFamily    = comboL(Prefs.codeFontFamily, default = Prefs.defaultCodeFontFamily,
      values = CodeView.availableFonts())
    val ggCodeFontSize      = intField(Prefs.codeFontSize, default = Prefs.defaultCodeFontSize, min = 4, max = 256)
    val ggCodeFont          = mkFlow(0, ggCodeFontFamily, ggCodeFontSize)

    val lbCodeFontStretch    = new Label
    val ggCodeFontStretch0  = intField(Prefs.codeFontStretch, default = Prefs.defaultCodeFontStretch, min = 50, max = 200)
    val ggCodeFontStretch   = mkFlow(4, new Label("Vertical Stretch [%]:"), ggCodeFontStretch0)

    val lbCodeLineSpacing   = label("Code Line Spacing [%]")
    val ggCodeLineSpacing   = intField(Prefs.codeLineSpacing, default = Prefs.defaultCodeLineSpacing, min = 90, max = 200)

    val icnWarn             = GUI.iconNormal(raphael.Shapes.Warning)
//    def lbWarnAppIcon       = Swing.HGlue // new Label(null, icnWarn, Alignment.Trailing)
    def lbWarnText(entries: Preferences.Entry[_]*): Component = {
      val lb = new Label(
        s"<html><body>Some changes take only effect<br>after restarting the application.",
        icnWarn, Alignment.Leading)
      val cfg = Mellite.config
      val res = if (!cfg.hasLauncher) lb else {
        val b = Button("Restart")(Mellite.tryRestart())
        new FlowPanel(lb, b)
      }
      res.visible = false
      entries.foreach(_.addListener {
        case _ => if (!res.visible) {
          res.visible = true
          pack()
        }
      })
      res
    }

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

    val lbAudioDevice   = label(if (Desktop.isLinux) "Jack Client Name" else "Audio Device")
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

    val lbTempDir = label("Temporary directory")
    val ggTempDir = pathField1(Prefs.tempDir, Prefs.defaultTempDir,
      title = "Directory for storing temporary files (e.g. during rendering)", mode = FileDialog.Folder)

    val lbLockTimeout   = label("Database Lock Timeout [ms]")
    val ggLockTimeout   = intField(Prefs.dbLockTimeout, Prefs.defaultDbLockTimeout)

    val lbCheckUpdates  = label("Automatic Update Checks")
    val pCheckUpdates   = Prefs.updateCheckPeriod
    val ggCheckUpdates  = comboL(pCheckUpdates, Prefs.defaultUpdateCheckPeriod, values = Seq(0, 1, 7, 30)) {
      case 0 => "Never"
      case 1 => "Daily"
      case 7 => "Weekly"
      case x if x >= 28 && x <= 31 => "Monthly"
      case x => s"Every $x Days"
    }

    // save the new interval in the launcher's properties file
    pCheckUpdates.addListener { case opt =>
      val days = opt.getOrElse(Prefs.defaultUpdateCheckPeriod)
      saveLauncherUpdateCheckPeriod(days)
    }

    // ---- panel ----

    val tabbed = new TabbedPane
    tabbed.peer.putClientProperty("styleId", "attached")  // XXX TODO: obsolete

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
        (lbScreenMenuBar    , ggScreenMenuBar   ),
        (lbCodeFont         , ggCodeFont        ),
        (lbCodeFontStretch  , ggCodeFontStretch ),
        (lbCodeLineSpacing  , ggCodeLineSpacing ),
      ),
      List(
        (Swing.HGlue /*lbWarnAppIcon*/, lbWarnText(Prefs.lookAndFeel, Prefs.nativeWindowDecoration, Prefs.screenMenuBar /*"appearance"*/))
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
        (lbSampleRate       , ggSampleRate      ),
      ),
      List(
        (lbBlockSize        , ggBlockSize       ),
        (lbNumPrivate       , ggNumPrivate      ),
        (lbNumAudioBufs     , ggNumAudioBufs    ),
        (lbNumWireBufs      , ggNumWireBufs     ),
        (lbMemorySize       , ggMemorySize      ),
        (lbLatency          , ggLatency         ),
      ),
      List(
        (lbHeadphones       , ggHeadphones      ),
      )
    )
    mkPage("Sensors")(
      List(
        (lbSensorProtocol   , ggSensorProtocol  ),
        (lbSensorPort       , ggSensorPort      ),
        (lbSensorAutoStart  , ggSensorAutoStart ),
        (lbSensorCommand    , ggSensorCommand   ),
        (lbSensorChannels   , ggSensorChannels  ),
      )
    )
    mkPage("System")(
      List(
        (lbTempDir          , ggTempDir         ),
        (lbLockTimeout      , ggLockTimeout     ),
        (lbCheckUpdates     , ggCheckUpdates    ),
      ),
      List(
        (Swing.HGlue /*lbWarnAppIcon*/, lbWarnText(Prefs.tempDir /*"system"*/))
      )
    )

    tabbed.selection.index = selectedTab.id
    tabbed
  }

  private def saveLauncherUpdateCheckPeriod(days: Int): Unit = {
    val autoCheck         = days > 0
    val updateInterval    = if (!autoCheck) Long.MaxValue else days * 24 * 60 * 60 * 1000L // milliseconds
    val p                 = new JProperties()
    val KeyNextUpdate     = "next-update"
    val KeyUpdateInterval = "update-interval"
    val propFileName      = "launcher.properties"
    val groupId           = "de.sciss"
    val appId             = "mellite"
    val appDirs           = AppDirsFactory.getInstance
    val configBase        = appDirs.getUserConfigDir(appId, /* version */ null, /* author */ groupId)
    val prefix            = Mellite.config.prefix
    val configBaseF       = new File(configBase , prefix)
    val propFile          = new File(configBaseF, propFileName)
    if (!propFile.isFile) return
    try {
      val fi = new FileInputStream(propFile)
      try {
        p.load(fi)
      } finally {
        fi.close()
      }

      val now             = System.currentTimeMillis()
      val oldNextTimeOpt  = Option(p.getProperty(KeyNextUpdate)).flatMap(s => Try(s.toLong).toOption)
      val newNextTime     = if (!autoCheck) Long.MaxValue else now + updateInterval
      val oldNextTime     = oldNextTimeOpt.getOrElse(newNextTime)
      val nextUpdateTime  = if (!autoCheck) newNextTime else math.min(oldNextTime, newNextTime)

      p.put(KeyNextUpdate     , nextUpdateTime.toString)
      p.put(KeyUpdateInterval , updateInterval.toString)
      val fo = new FileOutputStream(propFile)
      try {
        p.store(fo, "Mellite launcher")
      } finally {
        fo.close()
      }
    } catch {
      case NonFatal(ex) =>
        ex.printStackTrace()
    }
  }

  contents = box

  initNoMenuBarActions(box)

  title           = "Preferences"
  closeOperation  = Window.CloseDispose
  pack()
  desktop.Util.centerOnScreen(this)
  front()
}
