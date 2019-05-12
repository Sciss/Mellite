/*
 *  MainFrame.scala
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

package de.sciss.mellite.gui

import java.awt.{Color, Font}
import java.net.{URI, URL}
import java.nio.file.Path
import java.util.function.ToLongFunction

import de.sciss.audiowidgets.PeakMeter
import de.sciss.desktop.{Desktop, Menu, OptionPane, Preferences, Window, WindowHandler}
import de.sciss.icons.raphael
import de.sciss.lucre.stm.TxnLike
import de.sciss.lucre.swing.LucreSwing.{defer, deferTx}
import de.sciss.lucre.synth.{Bus, Group, Server, Synth, Txn}
import de.sciss.mellite.Mellite.applyAudioPreferences
import de.sciss.mellite.gui.impl.ApiBrowser
import de.sciss.mellite.{Application, Mellite, Prefs, executionContext, log, showTimelineLog}
import de.sciss.numbers.Implicits._
import de.sciss.{desktop, osc}
import de.sciss.synth.proc.gui.AudioBusMeter
import de.sciss.synth.proc.{AuralSystem, SensorSystem}
import de.sciss.synth.swing.ServerStatusPanel
import de.sciss.synth.{Client, SynthGraph, addAfter, addBefore, addToHead, addToTail, proc, Server => SServer}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.{Ref, atomic}
import scala.concurrent.{Future, blocking}
import scala.swing.Swing._
import scala.swing.event.{ButtonClicked, MouseClicked, ValueChanged}
import scala.swing.{Action, Alignment, BoxPanel, Button, CheckBox, Component, FlowPanel, Label, Orientation, Slider, ToggleButton}
import scala.util.{Failure, Success}

final class MainFrame extends desktop.impl.WindowImpl { me =>
  import de.sciss.mellite.Mellite.{auralSystem, sensorSystem}

  def handler: WindowHandler = Application.windowHandler

  private[this] val lbSensors = new Label("Sensors:")
  private[this] val lbAudio   = new Label("Audio:")

  private[this] lazy val ggSensors = {
    val res = new PeakMeter
    res.orientation   = Orientation.Horizontal
    res.holdPainted   = false
    res.rmsPainted    = false
    res
  }

  {
    lbSensors.horizontalAlignment = Alignment.Trailing
    lbAudio  .horizontalAlignment = Alignment.Trailing
    if (Prefs.useSensorMeters) {
      ggSensors.focusable = false
      // this is so it looks the same as the audio-boot button on OS X
      ggSensors.peer.putClientProperty("JButton.buttonType", "bevel")
      ggSensors.peer.putClientProperty("JComponent.sizeVariant", "small")
    }
    val p1    = lbSensors.preferredSize
    val p2    = lbAudio  .preferredSize
    p1.width  = math.max(p1.width, p2.width)
    p2.width  = p1.width
    lbSensors.preferredSize = p1
    lbAudio  .preferredSize = p2
  }

  private[this] val ggDumpSensors: CheckBox = new CheckBox("Dump") {
    listenTo(this)
    reactions += {
      case ButtonClicked(_) =>
        val dumpMode = if (selected) osc.Dump.Text else osc.Dump.Off
        step { implicit tx =>
          sensorSystem.serverOption.foreach { s =>
            // println(s"dump($dumpMode)")
            s.dump(dumpMode)
          }
        }
    }
    enabled = false
  }

  private[this] val actionStartStopSensors: Action = new Action("Start") {
    def apply(): Unit = {
      val isRunning = step { implicit tx =>
        sensorSystem.serverOption.isDefined
      }
      if (isRunning) stopSensorSystem() else Mellite.startSensorSystem()
    }
  }

  private[this] val sensorServerPane = new BoxPanel(Orientation.Horizontal) {
    contents += HStrut(4)
    contents += lbSensors
    contents += HStrut(4)
    contents += new Button(actionStartStopSensors) {
      focusable = false
    }
    contents += HStrut(16)
    contents += ggDumpSensors
    contents += HGlue
  }

  private[this] val audioServerPane = new ServerStatusPanel()
  audioServerPane.bootAction = Some(() => { Mellite.startAuralSystem(); () })

  private[this] val boxPane = new BoxPanel(Orientation.Vertical)
  if (Prefs.sensorChannels.getOrElse(0) > 0) boxPane.contents += sensorServerPane
  boxPane.contents += new BoxPanel(Orientation.Horizontal) {
    contents += HStrut(4)
    contents += lbAudio
    contents += HStrut(2)
    contents += audioServerPane
    contents += HGlue
  }

  // component.peer.getRootPane.putClientProperty("Window.style", "small")
  component.peer.getRootPane.putClientProperty("apple.awt.brushMetalLook", true)
  resizable = false
  contents  = boxPane

//  private[this] val iconOnline = GUI.sharpIcon(raphael.Shapes.Firefox, extent = 16)

  private[this] val iconOnline = raphael.Icon(extent = 14)(raphael.Shapes.Firefox)

  private class BrowseAction(uri: URI, text: String) extends Action(text) {
    icon = iconOnline

    def apply(): Unit = Desktop.browseURI(uri)
  }

  {
    import de.sciss.desktop.Menu.{Group, Item}
    val mf      = handler.menuFactory
    val gHelp   = Group("help", "Help")
    val itAbout = Item.About(Application)(showAbout())
    if (itAbout.visible) gHelp.add(itAbout)

    gHelp
      .add(Item("api")("API Documentation")(ApiBrowser.openBase(None)))
      .add(Item("shortcuts")("Keyboard Shortcuts")(Help.shortcuts()))
      .addLine()
      .add(Item("index" , new BrowseAction(new URI(Mellite.homepage), "Online Documentation")))
      .add(Item("issues", new BrowseAction(new URI("https://git.iem.at/sciss/Mellite/issues"), "Report an Issue")))
      .add(Item("chat"  , new BrowseAction(new URI("https://gitter.im/Sciss/Mellite") , "Chat Room")))
//      .add(Item("issues")("Report an Issue \u2197")(
//        Desktop.browseURI(new URI("https://git.iem.at/sciss/Mellite/issues"))))
//      .add(Item("chat")("Chat Room \u2197")(
//        Desktop.browseURI(new URI("https://gitter.im/Sciss/Mellite"))))

    mf.get("actions").foreach {
      case g: Menu.Group =>
        g.add(Some(this), Menu.Item("server-tree", ActionShowTree))
        g.add(Some(this), Menu.Item("toggle-debug-log")("Toggle Debug Log")(toggleDebugLog()))
      case _ =>
    }

    val me = Some(this)
    mf.add(me, gHelp)
  }

  private def showAbout(): Unit = {
    val url       = Mellite.homepage
    val addr      = url // url.substring(math.min(url.length, url.indexOf("//") + 2))
    val cacheDir  = Mellite.cacheDir
    var scVersion = "..."
    var cacheSize = "..."

    def html(): String =
      s"""<html><center>
         |<font size=+1><b>${Application.name}</b></font><p>
         |Version ${Mellite.version}<p>
         |<p>
         |Copyright (c) 2012&ndash;2019 Hanns Holger Rutz. All rights reserved.<p>
         |This software is published under the ${Mellite.license}
         |<p>&nbsp;<p><i>
         |Scala v${de.sciss.mellite.BuildInfo.scalaVersion}<br>
         |Java v${sys.props.getOrElse("java.version", "?")}<br>
         |SuperCollider server $scVersion<br>
         |Cache directory: $cacheDir $cacheSize
         |</i>
         |<p>&nbsp;<p>
         |<a href="$url">$addr</a>
         |<p>&nbsp;
         |""".stripMargin

    val lb = new Label(html()) {
      // cf. http://stackoverflow.com/questions/527719/how-to-add-hyperlink-in-jlabel
      // There is no way to directly register a HyperlinkListener, despite hyper links
      // being rendered... A simple solution is to accept any mouse click on the label
      // to open the corresponding website.
      cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
      listenTo(mouse.clicks)
      reactions += {
        case MouseClicked(_, _, _, 1, false) => Desktop.browseURI(new URL(url).toURI)
      }
    }

    def spawn[A](gen: => A)(success: A => Unit)(failure: => Unit): Unit = {
      val fut = Future(blocking(gen))
      fut.onComplete { tr =>
        defer {
          tr match {
            case Success(v) => success(v)
            case Failure(_) => failure
          }
          lb.text = html()
        }
      }
    }

    val serverCfg = Server.Config()
    // must be on EDT:
    applyAudioPreferences(serverCfg, Client.Config(), useDevice = false, pickPort = false)

    spawn {
      SServer.version(serverCfg).get
    } { case (v, b) =>
      val bs = if (b.isEmpty) b else s" ($b)"
      scVersion = s"v$v$bs"
    } {
      scVersion = "?"
    }

    spawn {
      import java.nio.file.Files
//      val size: Long = Files.walk(cacheDir.toPath).mapToLong((p: Path) => p.toFile.length).sum
      val size: Long = Files.walk(cacheDir.toPath).mapToLong(new ToLongFunction[Path] {
        def applyAsLong(p: Path): Long = p.toFile.length
      }).sum
      size
    } { sz =>
      cacheSize = s"(using ${(sz + 500000)/1000000} MB)"
    } {
      cacheSize = "(unknown size)"
    }

    OptionPane.message(message = lb.peer, icon = Logo.icon(128)).show(None, title = "About")
  }

  private def toggleDebugLog(): Unit = {
    val state = !showTimelineLog
    showTimelineLog         = state
    proc.showAuralLog       = state
    proc.showTransportLog   = state
  }

  private object ActionShowTree extends Action("Show Server Node Tree") {
    enabled = false

    private var sOpt = Option.empty[Server]

    def server: Option[Server] = sOpt
    def server_=(value: Option[Server]): Unit = {
      sOpt    = value
      enabled = sOpt.isDefined
    }

    def apply(): Unit =
      sOpt.foreach { server =>
        import de.sciss.synth.swing.Implicits._
        server.peer.gui.tree()
      }
  }

  private[this] val metersRef   = Ref(List.empty[AudioBusMeter])
  private[this] var onlinePane  = Option.empty[Component]

  private[this] val smallFont   = new Font("SansSerif", Font.PLAIN, 9)

  private def step[A](fun: Txn => A): A = atomic { implicit itx =>
    implicit val tx: Txn = Txn.wrap(itx)
    fun(tx)
  }

  private[this] val synOpt          = Ref(Option.empty[Synth])
  private[this] var ggMainVolumeOpt = Option.empty[Slider]

  def setMainVolume(linear: Double)(implicit tx: Txn): Unit = {
    synOpt.get(tx.peer).foreach { syn => syn.set("amp" -> linear) }
    deferTx {
      ggMainVolumeOpt.foreach { slid =>
        val db      = math.min(18, math.max(-72, (linear.ampDb + 0.5).toInt))
        slid.value  = db
      }
    }
  }

  private def auralSystemStarted(s: Server)(implicit tx: Txn): Unit = {
    log("MainFrame: AuralSystem started")

    val numIns  = s.peer.config.inputBusChannels
    val numOuts = s.peer.config.outputBusChannels

    val group   = Group.play(s.defaultGroup, addAfter)
    val mGroup  = Group.play(group, addToHead)

    val graph = SynthGraph {
      import de.sciss.synth._
      import Ops.stringToControl
      import ugen._
      val in        = In.ar(0, numOuts)
      val mainAmp   = Lag.ar("amp".kr(0f))
      val mainIn    = in * mainAmp
      val ceil      = -0.2.dbAmp
      val mainLim   = Limiter.ar(mainIn, level = ceil)
      val lim       = Lag.ar("limiter".kr(0f) * 2 - 1)
      // we fade between plain signal and limited signal
      // to allow for minimum latency when limiter is not used.
      val mainOut   = LinXFade2.ar(mainIn, mainLim, pan = lim)
      val hpBusL    = "hp-bus".kr(0f)
      val hpBusR    = hpBusL + 1
      val hpAmp     = Lag.ar("hp-amp".kr(0f))
      val hpInL     = Mix.tabulate((numOuts + 1) / 2)(i => in.out(i * 2))
      val hpInR     = Mix.tabulate( numOuts      / 2)(i => in.out(i * 2 + 1))
      val hpLimL    = Limiter.ar(hpInL * hpAmp, level = ceil)
      val hpLimR    = Limiter.ar(hpInR * hpAmp, level = ceil)

      val hpActive  = Lag.ar("hp".kr(0f))
      val out       = (0 until numOuts).map { i =>
        val isL   = hpActive & (hpBusL sig_== i)
        val isR   = hpActive & (hpBusR sig_== i)
        val isHP  = isL | isR
        (mainOut out i) * (1 - isHP) + hpLimL * isL + hpLimR * isR
      }

      ReplaceOut.ar(0, out)
    }

    val hpBus = Prefs.headphonesBus.getOrElse(Prefs.defaultHeadphonesBus)

    val syn = Synth.playOnce(graph = graph, nameHint = Some("master"))(target = group,
      addAction = addToTail, args = List("hp-bus" -> hpBus), dependencies = Nil)
    synOpt.set(Some(syn))(tx.peer)

    val meters = if (!Prefs.useAudioMeters) List.empty else {
      val res0 = if (numOuts == 0) Nil else {
        val outBus  = Bus.soundOut(s, numOuts )
        val mOut    = AudioBusMeter(AudioBusMeter.Strip(outBus, mGroup, addToHead) :: Nil)
        deferTx {
          mOut.component.tooltip = "Output Levels"  // as long as we do not have an explicit label component
        }
        mOut :: Nil
      }
      if (numIns == 0) res0 else {
        val inBus   = Bus.soundIn(s, numIns)
        val mIn     = AudioBusMeter(AudioBusMeter.Strip(inBus, s.defaultGroup, addBefore) :: Nil)
        deferTx {
          mIn.component.tooltip = "Input Levels"  // as long as we do not have an explicit label component
        }
        mIn :: res0
      }
    }
    metersRef.set(meters)(tx.peer)

    deferTx {
      mkAuralGUI(s = s, syn = syn, meters = meters, group = group, mGroup = mGroup)
    }
  }

  // group -- master group; mGroup -- metering group
  private def mkAuralGUI(s: Server, syn: Synth, meters: List[AudioBusMeter], group: Group, mGroup: Group): Unit = {
    ActionShowTree.server = Some(s)

    audioServerPane.server = Some(s.peer)

    val p = new FlowPanel() // new BoxPanel(Orientation.Horizontal)

    meters.foreach { m =>
      p.contents += m.component
      p.contents += HStrut(8)
    }

    def mkAmpFader(ctl: String, prefs: Preferences.Entry[Int]): Slider = mkFader(prefs, 0) { db =>
      import de.sciss.synth._
      val amp = if (db == -72) 0f else db.dbAmp
      step { implicit tx => syn.set(ctl -> amp) }
    }

    val ggMainVolume    = mkAmpFader("amp"   , Prefs.audioMasterVolume)
    val ggHPVolume      = mkAmpFader("hp-amp", Prefs.headphonesVolume )
    ggMainVolumeOpt = Some(ggMainVolume)

    def mkToggle(label: String, prefs: Preferences.Entry[Boolean], default: Boolean = false)
                (fun: Boolean => Unit): ToggleButton = {
      val res = new ToggleButton
      res.action = Action(label) {
        val v = res.selected
        fun(v)
        prefs.put(v)
      }
      res.peer.putClientProperty("JComponent.sizeVariant", "mini")
      res.peer.putClientProperty("JButton.buttonType", "square")
      val v0 = prefs.getOrElse(default)
      res.selected  = v0
      res.focusable = false
      fun(v0)
      res
    }

    val ggPost = mkToggle("post", Prefs.audioMasterPostMeter) { post =>
      step { implicit tx =>
        if (post) mGroup.moveToTail(group) else mGroup.moveToHead(group)
      }
    }

    val ggLim = mkToggle("limiter", Prefs.audioMasterLimiter) { lim =>
      val on = if (lim) 1f else 0f
      step { implicit tx => syn.set("limiter" -> on) }
    }

    val ggHPActive = mkToggle("active", Prefs.headphonesActive) { active =>
      val on = if (active) 1f else 0f
      step { implicit tx => syn.set("hp" -> on) }
    }

    def mkBorder(comp: Component, label: String): Unit = {
      val res = TitledBorder(LineBorder(Color.gray), label)
      res.setTitleFont(smallFont)
      res.setTitleColor(comp.foreground)
      res.setTitleJustification(javax.swing.border.TitledBorder.CENTER)
      comp.border = res
    }

    val stripMain = new BoxPanel(Orientation.Vertical) {
      contents += ggPost
      contents += ggLim
      contents += ggMainVolume
      mkBorder(this, "Main")
    }

    val stripHP = new BoxPanel(Orientation.Vertical) {
      contents += VStrut(ggPost.preferredSize.height)
      contents += ggHPActive
      contents += ggHPVolume
      mkBorder(this, "Phones")
    }

    p.contents += stripMain
    p.contents += stripHP

    p.contents += HGlue
    onlinePane = Some(p)

    boxPane.contents += p
    // resizable = true
    pack()
  }

  private def mkFader(prefs: Preferences.Entry[Int], default: Int)(fun: Int => Unit): Slider = {
    val zeroMark    = "0\u25C0"
    val lbMap: Map[Int, Label] = (-72 to 18 by 12).iterator.map { dec =>
      val txt = if (dec == -72) "-\u221E" else if (dec == 0) zeroMark else dec.toString
      val lb  = new Label(txt)
      lb.font = smallFont
      (dec, lb)
    } .toMap
    val lbZero = lbMap(0)

    val sl: Slider = new Slider {
      orientation       = Orientation.Vertical
      min               = -72
      max               =  18
      value             =   prefs.getOrElse(default)
      minorTickSpacing  =   3
      majorTickSpacing  =  12
      paintTicks        = true
      paintLabels       = true

      private var isZero = true  // will be initialized

      peer.putClientProperty("JComponent.sizeVariant", "small")
      peer.putClientProperty("JSlider.isFilled", true)   // used by Metal-lnf
      labels            = lbMap

      private def perform(store: Boolean): Unit = {
        val v = value
        fun(v)
        if (isZero) {
          if (v != 0) {
            isZero = false
            lbZero.text = "0"
            repaint()
          }
        } else {
          if (v == 0) {
            isZero = true
            lbZero.text = zeroMark
            repaint()
          }
        }
        if (store) prefs.put(v)
      }

      listenTo(this)
      reactions += {
        case ValueChanged(_) => perform(store = true)
      }
      perform(store = false)
    }

    sl
  }

  private def auralSystemStopped()(implicit tx: Txn): Unit = {
    log("MainFrame: AuralSystem stopped")
    metersRef .swap(Nil)(tx.peer).foreach(_.dispose())
    synOpt.swap(None)(tx.peer)
    deferTx {
      audioServerPane.server = None
      onlinePane.foreach { p =>
        onlinePane = None
        boxPane.contents.remove(boxPane.contents.indexOf(p))
        // resizable = false
        pack()
      }
    }
  }

  def stopSensorSystem(): Unit =
    step { implicit tx =>
      sensorSystem.stop()
    }

  private def sensorSystemStarted(s: SensorSystem.Server)(implicit tx: TxnLike): Unit = {
    log("MainFrame: SensorSystem started")
    deferTx {
      actionStartStopSensors.title = "Stop"
      ggDumpSensors.enabled = true
      if (Prefs.useSensorMeters) {
        ggSensors.numChannels   = Prefs.sensorChannels.getOrElse(Prefs.defaultSensorChannels)
        ggSensors.preferredSize = (260, ggSensors.numChannels * 4 + 2)
        sensorServerPane.contents += ggSensors
      }
      pack()
    }
  }

  private def sensorSystemStopped()(implicit tx: TxnLike): Unit = {
    log("MainFrame: SensorSystem stopped")
    deferTx {
      ggMainVolumeOpt = None
      actionStartStopSensors.title = "Start"
      ggDumpSensors.enabled   = false
      ggDumpSensors.selected  = false
      if (Prefs.useSensorMeters) {
        sensorServerPane.contents.remove(sensorServerPane.contents.size - 1)
      }
      pack()
    }
  }

  private def updateSensorMeter(value: Vec[Float]): Unit = {
    val b = Vec.newBuilder[Float]
    b.sizeHint(32)
    value.foreach { peak =>
      b += peak.pow(0.65f).linExp(0f, 1f, 0.99e-3f, 1f) // XXX TODO roughly linearized
      b += 0f
    }
    ggSensors.clearMeter()    // XXX TODO: should have option to switch off ballistics
    ggSensors.update(b.result())
  }

  step { implicit tx =>
    auralSystem.addClient(new AuralSystem.Client {
      def auralStarted(s: Server)(implicit tx: Txn): Unit = me.auralSystemStarted(s)
      def auralStopped()         (implicit tx: Txn): Unit = me.auralSystemStopped()
    })
    sensorSystem.addClient(new SensorSystem.Client {
      def sensorsStarted(s: SensorSystem.Server)(implicit tx: TxnLike): Unit = me.sensorSystemStarted(s)
      def sensorsStopped()                      (implicit tx: TxnLike): Unit = me.sensorSystemStopped()

      def sensorsUpdate(values: Vec[Float])     (implicit tx: TxnLike): Unit =
        if (Prefs.useSensorMeters) deferTx(updateSensorMeter(values))
    })
  }
  // XXX TODO: removeClient

  title           = Application.name
  closeOperation  = Window.CloseIgnore

  reactions += {
    case Window.Closing(_) => Desktop.mayQuit().foreach(_ => Application.quit())
  }

  pack()
  front()

  if (Prefs.audioAutoBoot  .getOrElse(false)) Mellite.startAuralSystem()
  if (Prefs.sensorAutoStart.getOrElse(false)) Mellite.startSensorSystem()
}