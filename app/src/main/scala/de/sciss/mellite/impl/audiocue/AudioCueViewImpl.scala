/*
 *  AudioCueViewImpl.scala
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

package de.sciss.mellite.impl.audiocue

import de.sciss.asyncfile.Ops._
import de.sciss.audiofile.{AudioFileSpec, AudioFileType}
import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.{Desktop, FileDialog, UndoManager, Util}
import de.sciss.fscape.GE
import de.sciss.icons.raphael
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.graph.{AudioFileIn => LWAudioFileIn}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Artifact, ArtifactLocation, BooleanObj, Cursor, DoubleObj, LongObj, Source, SpanLikeObj, SpanObj, Workspace}
import de.sciss.mellite.ActionBounce.FileFormat
import de.sciss.mellite.GUI.iconNormal
import de.sciss.mellite.impl.component.DragSourceButton
import de.sciss.mellite.impl.objview.AudioCueObjViewImpl
import de.sciss.mellite.impl.{WindowImpl, timeline}
import de.sciss.mellite.util.Gain
import de.sciss.mellite.{ActionBounce, ArtifactFrame, AudioCueView, CanBounce, DragAndDrop, GUI, Mellite, ObjView, ProcActions, SonogramManager, ViewState}
import de.sciss.model.impl.ModelImpl
import de.sciss.proc.gui.TransportView
import de.sciss.proc.{AudioCue, GenContext, Proc, Scheduler, Tag, TimeRef, Timeline, Transport, Universe}
import de.sciss.processor.impl.FutureProxy
import de.sciss.processor.{Processor, ProcessorLike}
import de.sciss.span.Span
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.graph.ScanIn
import de.sciss.{fscape, sonogram, synth}

import java.awt.Color
import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI
import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, Label, Orientation, Swing}
import scala.util.Try
import scala.util.control.NonFatal

object AudioCueViewImpl {
  def apply[T <: Txn[T]](obj: AudioCue.Obj[T])(implicit tx: T, universe: Universe[T]): AudioCueView[T] = {
    val value         = obj.value
    type I            = tx.I
    implicit val itx: I = tx.inMemory
    val timeline      = Timeline[I]()
    val srRatio       = value.spec.sampleRate / TimeRef.SampleRate
    val numFramesTL   = (value.spec.numFrames / srRatio).toLong
    val offsetTL      = value.offset
    val fullSpanTL    = Span(offsetTL, numFramesTL)

    @tailrec def findArtifact(in: AudioCue.Obj[T]): Option[Source[T, Artifact[T]]] = in match {
      case AudioCue.Obj               (a, _, _, _)  => Some(tx.newHandle(a))
      case AudioCue.Obj.Shift         (p, _)        => findArtifact(p)
      case AudioCue.Obj.ReplaceOffset (p, _)        => findArtifact(p)
      case AudioCue.Obj.Var           (vr)          => findArtifact(vr())
      case _: AudioCue.Obj.Const[T]                 => None
    }

    val artifactOptH = findArtifact(obj)

    // ---- we go through a bit of a mess here to convert S -> I ----
     val artifact     = value.artifact
     val artifactDir  = artifact.parentOption.get //  artifact.location.directory
     val iLoc         = ArtifactLocation.newVar[I](artifactDir)
     val iArtifact    = Artifact(iLoc, artifact) // iLoc.add(artifact.value)

    val audioCueI     = AudioCue.Obj[I](iArtifact, value.spec, 0L /*value.offset*/, value.gain)

    val (_, proc)     = ProcActions.insertAudioRegion[I](timeline, time = Span(0L, numFramesTL),
      /* track = 0, */ audioCue = audioCueI, gOffset = 0L /* , bus = None */)

    val diff = Proc[I]()
    val diffGr = SynthGraph {
      import synth._
      import ugen._
      val in0 = ScanIn(Proc.mainIn)
      // in0.poll(1, "audio-file-view")
      val in = if (value.numChannels == 1) Pan2.ar(in0) else in0  // XXX TODO
      Out.ar(0, in) // XXX TODO
    }
    diff.graph() = diffGr

    val output = proc.outputs.add(Proc.mainOut)
    diff.attr.put(Proc.mainIn, output)
    // val transport     = Transport[I, I](group, sampleRate = sampleRate)

    implicit val cursorI: Cursor[I] = tx.inMemoryCursor
    implicit val workspaceI: Workspace[I] = Workspace.Implicits.dummy[I](tx.system, cursorI)
    val genI        = GenContext[I]() // (itx, cursorI, workspaceI)
    val schI        = Scheduler[I]()
    val universeI   = Universe[I](genI, schI, universe.auralSystem)
    val transport   = Transport[I](universeI)
    transport.addObject(timeline) // Obj(Timeline(timeline)))
    transport.addObject(diff)

    val objH = tx.newHandle(obj)
    implicit val undo: UndoManager = UndoManager()
    val res: Impl[T, I] = new Impl[T, I](value, objH, artifactOptH, fullSpanTL, inMemoryBridge = tx.inMemoryBridge) {
      val timelineModel: TimelineModel.Modifiable =
        TimelineModel(bounds = fullSpanTL, visible = fullSpanTL, virtual = fullSpanTL,
          sampleRate = TimeRef.SampleRate)
      val transportView: TransportView[I] =
        TransportView[I](transport, timelineModel, hasMillis = true, hasLoop = true, hasCatch = true)
    }

    res.init(obj)
  }

  private final val StateKey_VisualBoost  = "vis-boost"
  private final val StateKey_Catch        = "catch"
  private final val StateKey_TlPosition   = "tl-pos"
  private final val StateKey_TlVisible    = "tl-vis"
  private final val StateKey_TlSelection  = "tl-sel"

  private abstract class Impl[T <: Txn[T], I <: Txn[I]](var value: AudioCue, val objH: Source[T, AudioCue.Obj[T]],
                                                        artifactOptH: Option[Source[T, Artifact[T]]],
                                                        fullSpanTL: Span,
                                                        inMemoryBridge: T => I)
                                                       (implicit val universe: Universe[T],
                                                        val undoManager: UndoManager)
    extends AudioCueView[T]
      with AudioCueObjViewImpl.Basic[T] with View.Editable[T]
      with ComponentHolder[Component]
      with CanBounce { impl =>

    type C = Component

    protected def transportView: TransportView[I]
    protected def timelineModel: TimelineModel.Modifiable

    private var _sonogram     : sonogram.Overview = _
    private var sonogramView  : AudioCueViewJ[I]  = _

    // ---- state ----

    @volatile
    private var stateVisualBoost  = 22.0
    private var dirtyVisualBoost  = false

    @volatile
    private var stateCatch        = true
    private var dirtyCatch        = false

    @volatile
    private var stateTlSelection  = Span.Void: Span.SpanOrVoid
    private var dirtyTlSelection  = false

    @volatile
    private var stateTlVisible    = Span(0L, 0L)
    private var dirtyTlVisible    = false

    @volatile
    private var stateTlPosition   = 0L
    private var dirtyTlPosition   = false

    override def viewState: Set[ViewState] = {
      requireEDT()
      var res = Set.empty[ViewState]
      if (dirtyVisualBoost) res += ViewState(StateKey_VisualBoost , DoubleObj   , stateVisualBoost)
      if (dirtyCatch      ) res += ViewState(StateKey_Catch       , BooleanObj  , stateCatch      )
      if (dirtyTlPosition ) res += ViewState(StateKey_TlPosition  , LongObj     , stateTlPosition )
      if (dirtyTlVisible  ) res += ViewState(StateKey_TlVisible   , SpanObj     , stateTlVisible  )
      if (dirtyTlSelection) res += ViewState(StateKey_TlSelection , SpanLikeObj , stateTlSelection)
      res
    }

    object actionBounce extends ActionBounce[T](impl, objH) {
      override protected def prepare(set0: ActionBounce.QuerySettings[T],
                                     recalled: Boolean): ActionBounce.QuerySettings[T] = {
        val sel   = timelineModel.selection
        val spec0 = value.spec
        val set1  = if (sel.isEmpty) set0 else set0.copy(span = sel)
        val set2  = if (recalled) set1 else set1.copy(
          fileFormat  = FileFormat.PCM(spec0.fileType, spec0.sampleFormat),
          sampleRate  = spec0.sampleRate.toInt,
          gain        = Gain.immediate(0.0),
        )
        set2
      }

      override protected def spanPresets(): SpanPresets = {
        val all = timelineModel.bounds match {
          case sp: Span => ActionBounce.SpanPreset("All", sp) :: Nil
          case _        => Nil
        }
        val sel = timelineModel.selection.nonEmptyOption.map(value => ActionBounce.SpanPreset("Selection", value)).toList
        all ::: sel
      }

      override protected def defaultFile(implicit tx: T): URI = {
        val a0 = value.artifact
        val a1 = a0.replaceName(s"${a0.base}Cut.${a0.extL}")
        a1
      }

      override protected def defaultChannels(implicit tx: T): Vec[Range.Inclusive] =
        Vector(Range.inclusive(0, value.spec.numChannels - 1))

      override protected def performGUI(settings: ActionBounce.QuerySettings[T], uri: URI, span: Span): Unit = {
        // println(s"performGUI($settings, $uri, $span)")
        val SR            = settings.sampleRate.toDouble
        val needsResample = SR != value.sampleRate
        val needsPost     = settings.fileFormat.isCompressed
        val span          = settings.span.nonEmptyOption.getOrElse(fullSpanTL)
        val srRatio       = value.spec.sampleRate / TimeRef.SampleRate
        val dropLen       = (span.start * srRatio + 0.5).toLong
        val takeLen       = (span.stop  * srRatio + 0.5).toLong - dropLen
        val numFramesIn   = value.spec.numFrames
        val numFramesOut  = takeLen
        val needsCrop     = numFramesIn != numFramesOut
        val fileOut       = new File(uri)
        val channels      = settings.channels.flatten
        val numChannels   = channels.size
        val selectChan    = channels != (0 until value.numChannels)
        val normalized    = settings.gain.normalized
        val needsFSc      = needsResample || needsCrop || selectChan || normalized || !needsPost

        // println(s"needsFSc $needsFSc: needsResample $needsResample || needsCrop $needsCrop || selectChan $selectChan || normalized $normalized || !needsPost !$needsPost")

        val (bncOutF: File, bncOut: URI, bncSpecOpt: Option[AudioFileSpec]) = if (needsFSc && needsPost) {
          val fTmp = File.createTempFile("bounce", ".w64")
          fTmp.deleteOnExit()
          val spec = AudioFileSpec(AudioFileType.Wave64, numChannels = numChannels, sampleRate = SR)
          (fTmp, fTmp.toURI, Some(spec))
        } else {
          // val FileFormat.PCM(tpe, smp) = settings.fileFormat
          (fileOut, uri, None) //  AudioFileSpec(tpe, smp, numChannels = numChannels, sampleRate = SR))
        }

        def gBnc(bncSpec: AudioFileSpec) = fscape.Graph {
          import fscape.Ops._
          import fscape.graph._

          def mkIn(): GE = {
            val in    = AudioFileIn(value.artifact, numChannels = value.spec.numChannels)
            val drop  = if (dropLen == 0L) in else in.drop(dropLen)
            val crop  = if (takeLen == numFramesIn - dropLen) drop else drop.take(numFramesOut)
            val rsmp  = if (!needsResample) crop else {
              Resample(crop, factor = SR / value.sampleRate)
            }
            lazy val zero = DC(0.0).take(numFramesOut)
            if (!selectChan) rsmp else channels.map { ch =>
              if (ch >= 0 && ch < value.numChannels) rsmp.out(ch) else zero
            }
          }

          val in0     = mkIn()
          val scaled  = if (!normalized) {
            val amp = settings.gain.linear
            if (amp == 1.0) in0 else in0 * amp
          } else {
            val ana = Reduce.max(RunningMax(in0.abs))
            ProgressFrames(ana, numFramesOut, "analyze")
            val max = ana.last
            // max.poll("MAX AMP")
            val amp = settings.gain.linear / (max + (max sig_== 0.0))
            val in1 = mkIn()
            in1 * amp
          }
          val written = AudioFileOut(scaled, bncOut, bncSpec)
          ProgressFrames(written, numFramesOut, "write")
        }

        import Mellite.executionContext

        def post(pre: ProcessorLike[File, Any]) = {
          val postGain = if (needsFSc) Gain.immediate(0.0) else settings.gain
          ActionBounce.postProcess(
            bounce      = pre,
            fileOut     = fileOut,
            fileFormat  = settings.fileFormat,
            gain        = postGain,
            numFrames   = numFramesOut,
          )
        }

        val p = if (needsFSc) {
          val pre: Processor[File] = new Processor[File] with FutureProxy[File]
            with ModelImpl[Processor.Update[File, Processor[File]]] { self =>

            private[this] var _progress = 0.0
            private[this] val ctrlCfg = {
              val b = fscape.stream.Control.Config()
              b.progressReporter = { pr =>
                _progress = pr.total
                dispatch(Processor.Progress(self, _progress))
              }
              b.build
            }
            private[this] val ctrl = fscape.stream.Control(ctrlCfg)

            protected def peerFuture: Future[File] = ctrl.status.map(_ => bncOutF)

            def abort(): Unit = ctrl.cancel()

            def progress: Double = _progress

            ctrl.run(gBnc(bncSpecOpt.get))
          }

          if (!needsPost) pre else post(pre)

        } else {
          val fileIn  = new File(value.artifact)
          val pre     = Processor.fromFuture("Bounce", Future.successful(fileIn))
          post(pre)
        }

        ActionBounce.monitorProcess(p, settings, uri, view = impl)
      }
    }

    override def dispose()(implicit tx: T): Unit = {
      val itx: I = inMemoryBridge(tx)
      transportView.transport.dispose()(itx)
      transportView.dispose()(itx)
//      gainView     .dispose()
      deferTx {
        if (_sonogram != null) SonogramManager.release(_sonogram)
      }
      super.dispose()
    }

    def init(obj: AudioCue.Obj[T])
            (implicit tx: T): this.type = {
      initAttrs(obj)
      for {
        attr  <- tx.attrMapOption(obj)
        tag   <- attr.$[Tag](WindowImpl.StateKey_Base)
        tAttr <- tx.attrMapOption(tag)
      } {
        tAttr.$[DoubleObj](StateKey_VisualBoost).foreach { v =>
          stateVisualBoost = v.value
        }
        tAttr.$[BooleanObj](StateKey_Catch).foreach { v =>
          stateCatch = v.value
        }
        tAttr.$[LongObj](StateKey_TlPosition).foreach { v =>
          stateTlPosition = v.value
        }
        tAttr.$[SpanObj](StateKey_TlVisible).foreach { v =>
          stateTlVisible = v.value
        }
        tAttr.$[SpanLikeObj](StateKey_TlSelection).foreach { v =>
          v.value match {
            case sp: Span => stateTlSelection = sp
            case _ =>
          }
        }
      }
      deferTx {
        guiInit()
      }
      this
    }

    private def guiInit(): Unit = {
      val snapshot = value

      var ggVisualBoost: Component = null

      try {
        val artF      = new File(snapshot.artifact)
        _sonogram     = SonogramManager.acquire(artF)
        val _transportView = transportView
        val _tlm = timelineModel
        _tlm.position = stateTlPosition
        if (stateTlVisible.nonEmpty) {
          _tlm.visible = stateTlVisible
        }
        _tlm.selection = stateTlSelection

        val _cueViewJ = new AudioCueViewJ[I](_sonogram, _transportView)
        val _catch    = _cueViewJ.transportCatch
        _catch.catchEnabled = stateCatch
        _catch.addListener {
          case b =>
            stateCatch  = b
            dirtyCatch  = true
        }
        sonogramView = _cueViewJ

        ggVisualBoost = GUI.boostRotaryR(init = stateVisualBoost.toFloat) { v =>
          stateVisualBoost = v.toDouble
          sonogramView.visualBoost = v
          dirtyVisualBoost = true
        }
        dirtyVisualBoost = false  // XXX TODO ugly. `boostRotaryR` always invokes function initially

        _tlm.addListener {
          case TimelineModel.Position (_, p) =>
            stateTlPosition   = p.now
            dirtyTlPosition   = true

          case TimelineModel.Visible(_, sp) =>
            stateTlVisible    = sp.now
            dirtyTlVisible    = true

          case TimelineModel.Selection(_, sp) =>
            stateTlSelection  = sp.now
            dirtyTlSelection  = true
        }

      } catch {
        case NonFatal(ex) =>
          ex.printStackTrace()
      }

      // val ggDragRegion = new DnD.Button(holder, snapshot, timelineModel)
      val ggDragObject = new DragSourceButton() {
        protected def createTransferable(): Option[Transferable] = {
          val artFOpt = Try(new File(snapshot.artifact)).toOption
          artFOpt.map { artF =>
            val t2 = DragAndDrop.Transferable.files(artF)
            val t3 = DragAndDrop.Transferable(ObjView.Flavor)(ObjView.Drag[T](universe, impl, Set.empty))
            val spOpt = timelineModel.selection match {
              case sp0: Span if sp0.nonEmpty => Some(sp0)
              case _ => timelineModel.bounds match {
                case sp0: Span  => Some(sp0)
                case _          => None
              }
            }
            val t1Opt = spOpt.map { sp =>
              val drag = timeline.DnD.AudioDrag[T](universe, objH, selection = sp)
              DragAndDrop.Transferable(timeline.DnD.flavor)(drag)
            }
            val t = t2 :: t3 :: t1Opt.toList
            DragAndDrop.Transferable.seq(t: _*)
          }
        }
        tooltip = "Drag Selected Region or File"
      }

      val topPane = new BoxPanel(Orientation.Horizontal) {
        contents ++= Seq(
          HStrut(4),
          ggDragObject,
          // new BusSinkButton[T](impl, ggDragRegion),
//          HStrut(4),
//          new Label("Gain:"),
//          gainView.component,
          HStrut(8),
          if (ggVisualBoost == null) HStrut(8) else ggVisualBoost,
          HGlue,
          HStrut(4),
          transportView.component,
          HStrut(4)
        )
      }

      val ggReveal        = new Button(Action(null) {
        val fileOpt = Try(new File(value.artifact)).toOption
        fileOpt.foreach(Desktop.revealFile)
      })
      ggReveal.peer.putClientProperty("styleId", "icon-hover")
      ggReveal.icon       = iconNormal(raphael.Shapes.Inbox)
      ggReveal.tooltip    = s"Reveal in ${if (Desktop.isMac) "Finder" else "File Manager"}"

      val ggArtifact      = new Button(Action(null) {
        artifactOptH.foreach { ah =>
          cursor.step { implicit tx =>
            val a = ah()
            ArtifactFrame(a, mode = false, initMode = FileDialog.Open)
          }
        }
      })
      ggArtifact.peer.putClientProperty("styleId", "icon-hover")
      ggArtifact.icon     = iconNormal(raphael.Shapes.PagePortrait)
      ggArtifact.tooltip  = "File View"
      ggArtifact.enabled  = artifactOptH.isDefined

      val lbSpec = new Label(LWAudioFileIn.specToString(snapshot.spec))

      val bottomPane = new BoxPanel(Orientation.Horizontal) {
        contents += ggReveal
        contents += ggArtifact
        contents += Swing.HStrut(4)
        contents += lbSpec
      }

      val pane = new BorderPanel {
        layoutManager.setVgap(2)
        add(topPane,    BorderPanel.Position.North  )
        add({
          if (sonogramView != null) sonogramView.component else {
            val lb = new Label("Error: File cannot be read.")
            lb.foreground = Color.red
            lb
          }
        }, BorderPanel.Position.Center )
        add(bottomPane, BorderPanel.Position.South  )
      }

      component = pane
      if (sonogramView != null) Util.setInitialFocus(sonogramView.canvasComponent)
    }
  }
}