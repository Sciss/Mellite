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

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.{Desktop, FileDialog, Util}
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.stm
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.graph.AudioFileIn
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.GUI.iconNormal
import de.sciss.mellite.impl.component.DragSourceButton
import de.sciss.mellite.impl.objview.AudioCueObjViewImpl
import de.sciss.mellite.impl.timeline
import de.sciss.mellite.{ArtifactFrame, AudioCueView, DragAndDrop, GUI, ObjView, ProcActions, SonogramManager}
import de.sciss.span.Span
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.graph.ScanIn
import de.sciss.synth.proc.gui.TransportView
import de.sciss.synth.proc.{AudioCue, GenContext, Proc, Scheduler, TimeRef, Timeline, Transport, Universe, Workspace}
import de.sciss.{sonogram, synth}

import scala.annotation.tailrec
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, BoxPanel, Button, Component, Label, Orientation, Swing}
import scala.util.control.NonFatal

object AudioCueViewImpl {
  def apply[S <: Sys[S]](obj: AudioCue.Obj[S])(implicit tx: S#Tx, universe: Universe[S]): AudioCueView[S] = {
    val value         = obj.value // .artifact // store.resolve(element.entity.value.artifact)
    // val sampleRate    = f.spec.sampleRate
    val system: S     = tx.system
    type I            = system.I // _workspace.I
    implicit val itx: I#Tx = system.inMemoryTx(tx) // inMemoryBridge(tx)
    val timeline      = Timeline[I] // proc.ProcGroup.Modifiable[I]
    // val groupObj      = Obj(ProcGroupElem(group))
    val srRatio       = value.spec.sampleRate / TimeRef.SampleRate
    // val fullSpanFile  = Span(0L, f.spec.numFrames)
    val numFramesTL   = (value.spec.numFrames / srRatio).toLong
    val fullSpanTL    = Span(0L, numFramesTL)

    @tailrec def findArtifact(in: AudioCue.Obj[S]): Option[stm.Source[S#Tx, Artifact[S]]] = in match {
      case AudioCue.Obj               (a, _, _, _)  => Some(tx.newHandle(a))
      case AudioCue.Obj.Shift         (p, _)        => findArtifact(p)
      case AudioCue.Obj.ReplaceOffset (p, _)        => findArtifact(p)
    }

    val artifactOptH = findArtifact(obj)

    // ---- we go through a bit of a mess here to convert S -> I ----
     val artifact     = value.artifact
     val artifactDir  = artifact.parent //  artifact.location.directory
     val iLoc         = ArtifactLocation.newVar[I](artifactDir)
     val iArtifact    = Artifact(iLoc, artifact) // iLoc.add(artifact.value)

    val audioCueI     = AudioCue.Obj[I](iArtifact, value.spec, value.offset, value.gain)

    val (_, proc)     = ProcActions.insertAudioRegion[I](timeline, time = Span(0L, numFramesTL),
      /* track = 0, */ audioCue = audioCueI, gOffset = 0L /* , bus = None */)

    val diff = Proc[I]
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

//    implicit val cursorI: stm.Cursor[I] = stm.Cursor.inMemory(system)
    implicit val systemI: I = system.inMemory
    implicit val workspaceI: Workspace[I] = Workspace.Implicits.dummy[I]
    val genI        = GenContext[I]() // (itx, cursorI, workspaceI)
    val schI        = Scheduler[I]()
    val universeI   = Universe[I](genI, schI, universe.auralSystem)
    val transport   = Transport[I](universeI)
    transport.addObject(timeline) // Obj(Timeline(timeline)))
    transport.addObject(diff)

    val objH = tx.newHandle(obj)
    val res: Impl[S, I] = new Impl[S, I](value, objH, artifactOptH, inMemoryBridge = system.inMemoryTx) {
      val timelineModel: TimelineModel =
        TimelineModel(bounds = fullSpanTL, visible = fullSpanTL, virtual = fullSpanTL,
          sampleRate = TimeRef.SampleRate)
      val transportView: TransportView[I]   = TransportView[I](transport, timelineModel, hasMillis = true, hasLoop = true)
    }

    res.init(obj)
  }

  private abstract class Impl[S <: Sys[S], I <: Sys[I]](var value: AudioCue, val objH: stm.Source[S#Tx, AudioCue.Obj[S]],
                                                        artifactOptH: Option[stm.Source[S#Tx, Artifact[S]]],
                                                        inMemoryBridge: S#Tx => I#Tx)
                                                       (implicit val universe: Universe[S])
    extends AudioCueView[S] with AudioCueObjViewImpl.Basic[S] with ComponentHolder[Component] { impl =>

    type C = Component

    protected def transportView: TransportView[I]
    protected def timelineModel: TimelineModel

    private var _sonogram: sonogram.Overview = _

    override def dispose()(implicit tx: S#Tx): Unit = {
      val itx: I#Tx = inMemoryBridge(tx)
      transportView.transport.dispose()(itx)
      transportView.dispose()(itx)
//      gainView     .dispose()
      deferTx {
        if (_sonogram != null) SonogramManager.release(_sonogram)
      }
      super.dispose()
    }

    def init(obj: AudioCue.Obj[S])
            (implicit tx: S#Tx): this.type = {
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
        _sonogram     = SonogramManager.acquire(snapshot.artifact)
        sonogramView  = new AudioCueViewJ(_sonogram, timelineModel)
        ggVisualBoost = GUI.boostRotary()(sonogramView.visualBoost = _)
      } catch {
        case NonFatal(ex) =>
          ex.printStackTrace()
      }

      // val ggDragRegion = new DnD.Button(holder, snapshot, timelineModel)
      val ggDragObject = new DragSourceButton() {
        protected def createTransferable(): Option[Transferable] = {
          val t2 = DragAndDrop.Transferable.files(snapshot.artifact)
          val t3 = DragAndDrop.Transferable(ObjView.Flavor)(ObjView.Drag(universe, impl))
          val spOpt = timelineModel.selection match {
            case sp0: Span if sp0.nonEmpty => Some(sp0)
            case _ => timelineModel.bounds match {
              case sp0: Span => Some(sp0)
              case _ => None
            }
          }
          val t1Opt = spOpt.map { sp =>
            val drag  = timeline.DnD.AudioDrag[S](universe, objH, selection = sp)
            DragAndDrop.Transferable(timeline.DnD.flavor)(drag)
          }
          val t = t2 :: t3 :: t1Opt.toList
          Some(DragAndDrop.Transferable.seq(t: _*))
        }
        tooltip = "Drag Selected Region or File"
      }

      val topPane = new BoxPanel(Orientation.Horizontal) {
        contents ++= Seq(
          HStrut(4),
          ggDragObject,
          // new BusSinkButton[S](impl, ggDragRegion),
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

      val ggReveal        = new Button(Action(null)(Desktop.revealFile(value.artifact)))
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