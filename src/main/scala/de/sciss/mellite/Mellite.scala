/*
 *  Mellite.scala
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

import de.sciss.desktop.impl.{SwingApplicationImpl, WindowHandlerImpl}
import de.sciss.desktop.{Menu, OptionPane, WindowHandler}
import de.sciss.file.File
import de.sciss.lucre.stm
import de.sciss.lucre.stm.TxnLike
import de.sciss.lucre.swing.requireEDT
import de.sciss.lucre.synth.{Server, Sys, Txn}
import de.sciss.mellite.gui.impl.document.DocumentHandlerImpl
import de.sciss.mellite.gui.{ActionOpenWorkspace, DocumentViewHandler, LogFrame, MainFrame, MenuBar}
import de.sciss.osc
import de.sciss.synth.Client
import de.sciss.synth.proc.{AuralSystem, Code, GenContext, Scheduler, SensorSystem, TimeRef, Universe, Workspace}
import javax.swing.UIManager

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.{TxnExecutor, atomic}
import scala.language.existentials
import scala.swing.Label
import scala.util.control.NonFatal

object Mellite extends SwingApplicationImpl[Application.Document]("Mellite") with Application with Init {
  @volatile
  private[this] var _config: Config = _

  def config: Config = _config

  import de.sciss.synth.proc
//  proc.showLog            = true
//  showLog                 = true
//  //  lucre.event    .showLog = true
//  //  lucre.confluent.showLog = true
//  proc.showAuralLog       = true
//  proc.showTransportLog   = true
//  showTimelineLog         = true
//  de.sciss.lucre.bitemp.impl.BiGroupImpl.showLog = true
//  // gui.impl.timeline.TimelineViewImpl.DEBUG = true
//  de.sciss.lucre.event.showLog = true
//  de.sciss.fscape.showStreamLog = true

  override def main(args: Array[String]): Unit = {
    val default = Config()

    val p = new scopt.OptionParser[Config]("mellite") {
      opt[Seq[String]]('r', "auto-run")
        .text("Run object with given name from root folder's top level. Comma separated list for multiple objects.")
        .action { (v, c) => c.copy(autoRun = v.toList) }

      arg[File]("<workspaces>")
        .unbounded()
        .optional()
        .text("Workspaces (.mllt directories) to open")
        .action { (v, c) => c.copy(open = c.open :+ v) }

      help("help").text("Prints this usage text")
    }
    p.parse(args, default).fold(sys.exit(1)) { config =>
      _config = config
      super.main(args)
    }
  }

  def version : String = buildInfString("version" )
  def license : String = buildInfString("license" )
  def homepage: String = buildInfString("homepage")

  private def buildInfString(key: String): String = try {
    val clazz = Class.forName("de.sciss.mellite.BuildInfo")
    val m     = clazz.getMethod(key)
    m.invoke(null).toString
  } catch {
    case NonFatal(_) => "?"
  }

  lazy val isDarkSkin: Boolean = UIManager.getBoolean("dark-skin")

  override lazy val windowHandler: WindowHandler = new WindowHandlerImpl(this, menuFactory) {
    override lazy val usesInternalFrames: Boolean = {
      false // XXX TODO: eventually a preferences entry
    }

    override lazy val usesNativeDecoration: Boolean = Prefs.nativeWindowDecoration.getOrElse(true)
  }

  protected def menuFactory: Menu.Root = MenuBar.instance

  private lazy val _aural     = AuralSystem(global = true)
  private lazy val _sensor    = SensorSystem()
  private lazy val _compiler  = proc.Compiler()

  implicit def auralSystem : AuralSystem   = _aural
  implicit def sensorSystem: SensorSystem  = _sensor
  implicit def compiler    : Code.Compiler = _compiler

  def clearLog  (): Unit = LogFrame.instance.log.clear()
  def logToFront(): Unit = LogFrame.instance.front()  // XXX TODO - should avoid focus transfer


  /** Tries to start the aural system by booting SuperCollider.
    * This reads the relevant preferences entries such as
    * path, audio device, number of output channels, etc.
    * Transport is hard-coded to TCP at the moment, and port
    * is randomly picked.
    *
    * If the program path does not denote an existing file,
    * an error dialog is shown, and the method simply returns `false`.
    *
    * ''Note:'' This method must run on the EDT.
    *
    * @return `true` if the attempt to boot was made, `false` if the program was not found
    */
  def startAuralSystem(): Boolean = {
    requireEDT()
    val serverCfg = Server.Config()
    val clientCfg = Client.Config()
    applyAudioPreferences(serverCfg, clientCfg, useDevice = true, pickPort = true)
    import de.sciss.file._

    val f = file(serverCfg.program)
    if (!f.isFile && (f.parentOption.nonEmpty || {
      sys.env.getOrElse("PATH", "").split(File.pathSep).forall(p => !(file(p) / serverCfg.program).isFile)
    })) {
      val msg = new Label(
        s"""<HTML><BODY><B>The SuperCollider server program 'scsynth'<BR>
           |is not found at this location:</B><P>&nbsp;<BR>${serverCfg.program}<P>&nbsp;<BR>
           |Please adjust the path in the preferences.</BODY>""".stripMargin
      )
      val opt = OptionPane.message(msg, OptionPane.Message.Error)
      opt.show(title = "Starting Aural System")
      return false
    }

    TxnExecutor.defaultAtomic { implicit itx =>
      implicit val tx: Txn = Txn.wrap(itx)
      auralSystem.start(serverCfg, clientCfg)
    }
    true
  }

  def applyAudioPreferences(serverCfg: Server.ConfigBuilder, clientCfg: Client.ConfigBuilder,
                            useDevice: Boolean, pickPort: Boolean): Unit = {
    requireEDT()
    import de.sciss.file._
    val programPath         = Prefs.superCollider.getOrElse(Prefs.defaultSuperCollider)
    if (programPath != Prefs.defaultSuperCollider) serverCfg.program = programPath.path

    if (useDevice) {
      val audioDevice       = Prefs.audioDevice     .getOrElse(Prefs.defaultAudioDevice)
      if (audioDevice != Prefs.defaultAudioDevice) serverCfg.deviceName = Some(audioDevice)
    }
    val numOutputs          = Prefs.audioNumOutputs   .getOrElse(Prefs.defaultAudioNumOutputs)
    serverCfg.outputBusChannels = numOutputs
    val numInputs           = Prefs.audioNumInputs    .getOrElse(Prefs.defaultAudioNumInputs)
    serverCfg.inputBusChannels  = numInputs
    val numPrivate          = Prefs.audioNumPrivate   .getOrElse(Prefs.defaultAudioNumPrivate)
    serverCfg.audioBusChannels = numInputs + numOutputs + numPrivate
    serverCfg.audioBuffers  = Prefs.audioNumAudioBufs .getOrElse(Prefs.defaultAudioNumAudioBufs)
    serverCfg.wireBuffers   = Prefs.audioNumWireBufs  .getOrElse(Prefs.defaultAudioNumWireBufs)
    serverCfg.sampleRate    = Prefs.audioSampleRate   .getOrElse(Prefs.defaultAudioSampleRate)
    serverCfg.blockSize     = Prefs.audioBlockSize    .getOrElse(Prefs.defaultAudioBlockSize)
    serverCfg.memorySize    = Prefs.audioMemorySize   .getOrElse(Prefs.defaultAudioMemorySize) * 1024

    if (pickPort) {
      serverCfg.transport = osc.TCP
      serverCfg.pickPort()
    }

    clientCfg.latency = Prefs.audioLatency.getOrElse(Prefs.defaultAudioLatency) * 0.001
  }

  def startSensorSystem(): Unit = {
    val config = SensorSystem.Config()
    config.osc = Prefs.defaultSensorProtocol match {
      case osc.UDP => osc.UDP.Config()
      case osc.TCP => osc.TCP.Config()
    }
    config.osc.localPort  = Prefs.sensorPort   .getOrElse(Prefs.defaultSensorPort   )
    config.command        = Prefs.sensorCommand.getOrElse(Prefs.defaultSensorCommand)

    atomic { implicit itx =>
      implicit val tx: TxnLike = TxnLike.wrap(itx)
      sensorSystem.start(config.build)
    }
  }

  override protected def init(): Unit = {
    Application.init(this)

    // ---- look and feel

    try {
      val lafInfo = Prefs.lookAndFeel.getOrElse {
        val res = Prefs.LookAndFeel.default
        Prefs.lookAndFeel.put(res)
        res
      }
      lafInfo.install()
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }

    // I don't remember -- what was this about?
    // I think space bar hijacking in the timeline frame
    UIManager.getDefaults.remove("SplitPane.ancestorInputMap")

    // ---- type extensions ----
    // since some are registering view factories,
    // and those might use `isDarkSkin`, we place
    // this call after `lafInfo.install()`.

    initTypes()

    if (Prefs.useLogFrame) LogFrame.instance    // init
    DocumentViewHandler.instance    // init

    new MainFrame

    config.open.foreach { fIn =>
      val fut = ActionOpenWorkspace.perform(fIn)
      if (config.autoRun.nonEmpty) fut.foreach { ws =>
        Mellite.withUniverse(ws)(autoRun(_))
//        val ws1 = ws.asInstanceOf[Workspace[S] forSome { type S <: Sys[S] }]
//        autoRun(ws1)
      }
    }
  }

  /** Utility method that helps avoid type-casts in other places. */
  def withWorkspace[A](w: Workspace[_])(fun: (Workspace[S] forSome { type S <: Sys[S] }) => A): A = {
    fun.asInstanceOf[Workspace[_] => A](w)
  }

  /** Utility method that helps avoid type-casts in other places. */
  def withUniverse[A](u: Universe[_])(fun: (Universe[S] forSome { type S <: Sys[S] }) => A): A = {
    fun.asInstanceOf[Universe[_] => A](u)
  }

  def mkUniverse[S <: Sys[S]](implicit w: Workspace[S]): Universe[S] = {
    implicit val c: stm.Cursor[S] = w.cursor
    c.step { implicit tx =>
      val gen = GenContext[S]()
      val sch = Scheduler [S]()
      Universe(gen, sch, Mellite.auralSystem)
    }
  }

  private def autoRun[S <: Sys[S]](u: Universe[S]): Unit = {
    u.cursor.step { implicit tx =>
      val f = u.workspace.root
      config.autoRun.foreach { name =>
        import proc.Implicits._
        (f / name).fold[Unit] {
          tx.afterCommit(println(s"Warning: auto-run object '$name' does not exist."))
        } { obj =>
          u.mkRunner(obj).fold[Unit] {
            tx.afterCommit(println(s"Warning: no runner for object '$name' of type ${obj.tpe}."))
          } { r =>
            val timeRef = TimeRef.undefined
            r.run(timeRef, ())
          }
        }
      }
    }
  }

  /** We are bridging between the transactional and non-EDT `mellite.DocumentHandler` and
    * the GUI-based `de.sciss.desktop.DocumentHandler`. This is a bit ugly. In theory it
    * should be fine to call into either, as this bridge is backed up by the peer
    * `mellite.DocumentHandler.instance`.
    */
  override lazy val documentHandler: DocumentHandler = new DocumentHandlerImpl

  // ---- Application trait ----

  lazy val topLevelObjects: ISeq[String] =
    List("Folder", "AudioCue", "Proc", "Timeline")

  /** All objects included */
  lazy val objectFilter: String => Boolean = _ => true
}