/*
 *  AudioCueViewImpl.scala
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

package de.sciss.mellite.impl.audiocue

import java.awt.Color
import java.awt.datatransfer.Transferable

import de.sciss.asyncfile.Ops._
import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.{Desktop, FileDialog, Util}
import de.sciss.file.File
import de.sciss.icons.raphael
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.graph.AudioFileIn
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Artifact, ArtifactLocation, Cursor, Source, Workspace}
import de.sciss.mellite.GUI.iconNormal
import de.sciss.mellite.impl.component.DragSourceButton
import de.sciss.mellite.impl.objview.AudioCueObjViewImpl
import de.sciss.mellite.impl.timeline
import de.sciss.mellite.{ArtifactFrame, AudioCueView, DragAndDrop, GUI, ObjView, ProcActions, SonogramManager}
import de.sciss.span.Span
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.graph.ScanIn
import de.sciss.synth.proc.gui.TransportView
import de.sciss.synth.proc.{AudioCue, GenContext, Proc, Scheduler, TimeRef, Timeline, Transport, Universe}
import de.sciss.{sonogram, synth}

import scala.annotation.tailrec
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, Label, Orientation, Swing}
import scala.util.Try
import scala.util.control.NonFatal

object AudioCueViewImpl {
  def apply[T <: Txn[T]](obj: AudioCue.Obj[T])(implicit tx: T, universe: Universe[T]): AudioCueView[T] = {
    val value         = obj.value // .artifact // store.resolve(element.entity.value.artifact)
    // val sampleRate    = f.spec.sampleRate
//    val system: S     = tx.system
    type I            = tx.I // _workspace.I
    implicit val itx: I = tx.inMemory // inMemoryBridge(tx)
    val timeline      = Timeline[I]() // proc.ProcGroup.Modifiable[I]
    // val groupObj      = Obj(ProcGroupElem(group))
    val srRatio       = value.spec.sampleRate / TimeRef.SampleRate
    // val fullSpanFile  = Span(0L, f.spec.numFrames)
    val numFramesTL   = (value.spec.numFrames / srRatio).toLong
    val fullSpanTL    = Span(0L, numFramesTL)

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

    val audioCueI     = AudioCue.Obj[I](iArtifact, value.spec, value.offset, value.gain)

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
    val res: Impl[T, I] = new Impl[T, I](value, objH, artifactOptH, inMemoryBridge = tx.inMemoryBridge) {
      val timelineModel: TimelineModel =
        TimelineModel(bounds = fullSpanTL, visible = fullSpanTL, virtual = fullSpanTL,
          sampleRate = TimeRef.SampleRate)
      val transportView: TransportView[I]   = TransportView[I](transport, timelineModel, hasMillis = true, hasLoop = true)
    }

    res.init(obj)
  }

  private abstract class Impl[T <: Txn[T], I <: Txn[I]](var value: AudioCue, val objH: Source[T, AudioCue.Obj[T]],
                                                        artifactOptH: Option[Source[T, Artifact[T]]],
                                                        inMemoryBridge: T => I)
                                                       (implicit val universe: Universe[T])
    extends AudioCueView[T] with AudioCueObjViewImpl.Basic[T] with ComponentHolder[Component] { impl =>

    type C = Component

    protected def transportView: TransportView[I]
    protected def timelineModel: TimelineModel

    private var _sonogram: sonogram.Overview = _

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
      deferTx {
        guiInit()
      }
      this
    }

    private def guiInit(): Unit = {
      val snapshot = value

      var sonogramView  : AudioCueViewJ = null
      var ggVisualBoost : Component     = null

      try {
        val artF      = new File(snapshot.artifact)
        _sonogram     = SonogramManager.acquire(artF)
        sonogramView  = new AudioCueViewJ(_sonogram, timelineModel)
        ggVisualBoost = GUI.boostRotary()(sonogramView.visualBoost = _)
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
            val t3 = DragAndDrop.Transferable(ObjView.Flavor)(ObjView.Drag(universe, impl))
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

      val lbSpec = new Label(AudioFileIn.specToString(snapshot.spec))

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