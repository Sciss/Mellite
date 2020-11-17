/*
 *  Mellite.scala
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

package de.sciss.mellite

import de.sciss.desktop.impl.{SwingApplicationImpl, WindowHandlerImpl}
import de.sciss.desktop.{Desktop, Menu, OptionPane, WindowHandler}
import de.sciss.file._
import de.sciss.log.Level
import de.sciss.lucre.synth.{RT, Server, Txn}
import de.sciss.lucre.{Cursor, TxnLike, Workspace}
import de.sciss.mellite.impl.document.DocumentHandlerImpl
import de.sciss.{osc, proc}
import de.sciss.proc.{AuralSystem, Code, GenContext, Scheduler, SensorSystem, SoundProcesses, Universe}
import de.sciss.synth.Client
import org.rogach.scallop.{ScallopConf, ScallopOption => Opt}

import java.util.Locale
import javax.swing.UIManager
import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.ExecutionContext
import scala.concurrent.stm.{TxnExecutor, atomic}
import scala.language.existentials
import scala.swing.Label
import scala.util.control.NonFatal

object Mellite extends SwingApplicationImpl[Application.Document]("Mellite") with Application with Init {
  @volatile
  private[this] var _config: Config = Config()

  def config: Config = _config

//  ServerImpl.USE_COMPRESSION = false

  /** An execution context for UI actions. This is `ExecutionContext.global` and _not_ the
    * perhaps single-threaded context provided by `Executor.executionContext`,
    * which may cause problems when used in blocking operations such as `Await`.
    */
  implicit val executionContext: ExecutionContext = ExecutionContext.global

  /** Exception are sometimes swallowed without printing in a transaction. This ensures a print. */
  def ???! : Nothing = {
    val err = new NotImplementedError
    err.printStackTrace()
    throw err
  }

  override def main(args: Array[String]): Unit = {
    try {
      // all UI and number formatters assume US locale
      Locale.setDefault(Locale.US)
    } catch {
      case _: Exception => ()
    }
    object p extends ScallopConf(args) {
      printedName = "mellite"
      version(fullName)

      val workspaces: Opt[List[File]] = trailArg(required = false, default = Some(Nil),
        descr = "Workspaces (.mllt directories) to open"
      )
      val headless: Opt[Boolean] = opt("headless", short = 'h',
        descr = "Run without graphical user-interface (Note: does not initialize preferences)."
      )
      val bootAudio: Opt[Boolean] = opt("boot-audio", short = 'b',
        descr = "Boot audio server when in headless mode."
      )
      val noLogFrame: Opt[Boolean] = opt("no-log-frame", short = 'n',
        descr = "Do not create log frame (post window)."
      )
      val autoRun: Opt[List[String]] = opt[String]("auto-run", short = 'r', default = Some(""),
        descr = "Run object with given name from root folder's top level. Comma separated list for multiple objects."
      ).map(_.split(',').filter(_.nonEmpty).toList)

      verify()
      val config: Config = Config(
        open      = workspaces(),
        headless  = headless(),
        bootAudio = bootAudio(),
        autoRun   = autoRun(),
        logFrame  = !noLogFrame(),
      )
    }

    _config = p.config
//    Console.setErr(System.err)

    if (_config.headless) {
      init()
    } else {
      super.main(args)
    }
  }

  def version : String = buildInfString("version" )
  def license : String = buildInfString("license" )
  def homepage: String = buildInfString("homepage")
  def fullName: String = s"$name v$version"

  private def buildInfString(key: String): String = try {
    val clazz = Class.forName("de.sciss.mellite.BuildInfo")
    val m     = clazz.getMethod(key)
    m.invoke(null).toString
  } catch {
    case NonFatal(_) => "?"
  }

  override lazy val windowHandler: WindowHandler = new WindowHandlerImpl(this, menuFactory) {
    override lazy val usesInternalFrames: Boolean = {
      false // XXX TODO: eventually a preferences entry
    }

    override lazy val usesNativeDecoration: Boolean =
      Prefs.nativeWindowDecoration.getOrElse(Prefs.defaultNativeWindowDecoration)
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
//    requireEDT()
    val serverCfg = Server.Config()
//    serverCfg.verbosity = -1
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
      implicit val tx: RT = RT.wrap(itx)
      auralSystem.start(serverCfg, clientCfg)
    }
    true
  }

  override def applyAudioPreferences(serverCfg: Server.ConfigBuilder, clientCfg: Client.ConfigBuilder,
                                     useDevice: Boolean, pickPort: Boolean): Unit = {
//    requireEDT()
    import de.sciss.file._
    import de.sciss.numbers.Implicits._
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
    serverCfg.audioBusChannels = (numInputs + numOutputs + numPrivate).nextPowerOfTwo
    serverCfg.audioBuffers  = Prefs.audioNumAudioBufs .getOrElse(Prefs.defaultAudioNumAudioBufs).nextPowerOfTwo
    serverCfg.wireBuffers   = Prefs.audioNumWireBufs  .getOrElse(Prefs.defaultAudioNumWireBufs)
    serverCfg.sampleRate    = Prefs.audioSampleRate   .getOrElse(Prefs.defaultAudioSampleRate)
    serverCfg.blockSize     = Prefs.audioBlockSize    .getOrElse(Prefs.defaultAudioBlockSize).nextPowerOfTwo
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
      implicit val tx: TxnLike = RT.wrap(itx)
      sensorSystem.start(config.build)
    }
  }

  private def initUI(): Unit = {
    if (Desktop.isMac) {
      System.setProperty("apple.laf.useScreenMenuBar",
        Prefs.screenMenuBar.getOrElse(Prefs.defaultScreenMenuBar).toString)
    }

    try {
      val lafInfo = Prefs.lookAndFeel.getOrElse {
        val res = Prefs.LookAndFeel.default
        Prefs.lookAndFeel.put(res)
        res
      }

      //      if (lafInfo.description.contains("Submin")) {
      //        WebLookAndFeel.globalControlFont  = WebLookAndFeel.globalControlFont.deriveFont(14f)
      //        WebLookAndFeel.globalMenuFont     = WebLookAndFeel.globalMenuFont   .deriveFont(14f)
      //        WebLookAndFeel.globalTextFont     = WebLookAndFeel.globalTextFont   .deriveFont(14f)
      //      }

      lafInfo.install()

    } catch {
      case NonFatal(e) => e.printStackTrace()
    }

    // I don't remember -- what was this about?
    // I think space bar hijacking in the timeline frame
    val uid = UIManager.getDefaults
    uid.remove("SplitPane.ancestorInputMap")
    uid.remove("TabbedPane.ancestorInputMap") // interferes with code editor navigation

    // early, so error printing in `initTypes` is already captured
    if (Prefs.useLogFrame && config.logFrame) {
      LogFrame.init()
    }

    SoundProcesses.log.level = Level.Warn

    DocumentViewHandler.init()
    OpenWorkspace.install()

    // at this point, awt.Toolkit will have loaded Atk
    System.getProperty("javax.accessibility.assistive_technologies") match {
      case "org.GNOME.Accessibility.AtkWrapper" =>
        println(
          s"""WARNING: Assistive technology installed
             |  that is known to cause performance problems.
             |
             |  It is recommended to create a plain text file
             |  `$userHome/.accessibility.properties`
             |  with contents
             |
             |  javax.accessibility.assistive_technologies=
             |
             |""".stripMargin
        )

      case _ =>
    }
  }

  // runs on the EDT (unless `config.headless`)
  override protected def init(): Unit = {
    // if (lafInfo.description.contains("Submin")) {
    // }

    Application.init(this)

    val headless = _config.headless

    // ---- temporary directory ----

    if (!headless) {  // XXX TODO --- we should have headless preferences access
      val tempDir = Prefs.tempDir.getOrElse(Prefs.defaultTempDir)
      if (tempDir != Prefs.defaultTempDir) {
        System.setProperty("java.io.tmpdir", tempDir.getPath)
      }
    }

    // ---- look and feel ----

    if (!headless) initUI()

    // ---- type extensions ----
    // since some are registering view factories,
    // and those might use `isDarkSkin`, we place
    // this call after `lafInfo.install()`.

    initTypes()

    // ---- expr ----

    if (!headless) {  // XXX TODO --- we should have headless preferences access
      // XXX TODO ugly
      de.sciss.lucre.expr.graph.Bounce.applyAudioPreferences =
        applyAudioPreferences(_, _, useDevice = false, pickPort = false)
    }

    // ---- main window and boot ----

    if (headless) {
      if (_config.bootAudio) {
//        Swing.onEDT {
          Mellite.startAuralSystem()
//        }
      }
    } else {
      new MainFrame
    }

    // ---- workspaces and auto-run ----

    config.open.foreach { fIn0 =>
      val fIn = fIn0.absolute
      val fut = if (headless) {
        OpenWorkspace.open(fIn, headless = true)
      } else {
        OpenWorkspace.perform(fIn)
      }
      if (config.autoRun.nonEmpty) fut.foreach { ws =>
        Mellite.withUniverse(ws)(autoRun(_))
//        val ws1 = ws.asInstanceOf[Workspace[T] forSome { type T <: Txn[T] }]
//        autoRun(ws1)
      }
    }
  }

  /** Utility method that helps avoid type-casts in other places. */
  def withWorkspace[A](w: Workspace[_])(fun: (Workspace[T] forSome { type T <: Txn[T] }) => A): A = {
    fun.asInstanceOf[Workspace[_] => A](w)
  }

  /** Utility method that helps avoid type-casts in other places. */
  def withUniverse[A](u: Universe[_])(fun: (Universe[T] forSome { type T <: Txn[T] }) => A): A = {
    fun.asInstanceOf[Universe[_] => A](u)
  }

  def mkUniverse[T <: Txn[T]](implicit w: Workspace[T]): Universe[T] = {
    implicit val c: Cursor[T] = w.cursor
    c.step { implicit tx =>
      val gen = GenContext[T]()
      val sch = Scheduler [T]()
      Universe(gen, sch, Mellite.auralSystem)
    }
  }

  private def autoRun[T <: Txn[T]](u: Universe[T]): Unit = {
    u.cursor.step { implicit tx =>
      val f = u.workspace.root
      config.autoRun.foreach { name =>
        import de.sciss.proc.Implicits._

        (f / name).fold[Unit] {
          tx.afterCommit(Log.log.warn(s"o-run object '$name' does not exist."))
        } { obj =>
          u.mkRunner(obj).fold[Unit] {
            tx.afterCommit(Log.log.warn(s"no runner for object '$name' of type ${obj.tpe}."))
          } { r =>
            r.run()
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
    List("Folder", "AudioCue", "Proc", "Timeline"/*, "Control"*/)

  /** All objects included */
  lazy val objectFilter: String => Boolean = _ => true
}