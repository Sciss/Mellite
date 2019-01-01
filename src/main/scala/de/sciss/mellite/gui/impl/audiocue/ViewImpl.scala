/*
 *  ViewImpl.scala
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
package gui
package impl
package audiocue

import java.awt.datatransfer.Transferable

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.Util
import de.sciss.file._
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.stm
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.component.DragSourceButton
import de.sciss.span.Span
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.AudioCue.Obj
import de.sciss.synth.proc.graph.ScanIn
import de.sciss.synth.proc.gui.TransportView
import de.sciss.synth.proc.{AudioCue, GenContext, Proc, Scheduler, TimeRef, Timeline, Transport, Universe, Workspace}
import de.sciss.{sonogram, synth}

import scala.swing.Swing._
import scala.swing.{BorderPanel, BoxPanel, Component, Orientation}

object ViewImpl {
  def apply[S <: Sys[S]](obj0: AudioCue.Obj[S])(implicit tx: S#Tx, universe: Universe[S]): AudioFileView[S] = {
    val audioCue      = obj0
    val audioCueV     = audioCue.value // .artifact // store.resolve(element.entity.value.artifact)
    // val sampleRate    = f.spec.sampleRate
    val system: S     = tx.system
    type I            = system.I // _workspace.I
    implicit val itx: I#Tx = system.inMemoryTx(tx) // inMemoryBridge(tx)
    val timeline      = Timeline[I] // proc.ProcGroup.Modifiable[I]
    // val groupObj      = Obj(ProcGroupElem(group))
    val srRatio       = audioCueV.spec.sampleRate / TimeRef.SampleRate
    // val fullSpanFile  = Span(0L, f.spec.numFrames)
    val numFramesTL   = (audioCueV.spec.numFrames / srRatio).toLong
    val fullSpanTL    = Span(0L, numFramesTL)

    // ---- we go through a bit of a mess here to convert S -> I ----
     val artifact      = obj0.value.artifact
     val artifDir      = artifact.parent //  artifact.location.directory
     val iLoc          = ArtifactLocation.newVar[I](artifDir)
     val iArtifact     = Artifact(iLoc, artifact) // iLoc.add(artifact.value)

    val audioCueI     = AudioCue.Obj[I](iArtifact, audioCueV.spec, audioCueV.offset, audioCueV.gain)

    val (_, proc)     = ProcActions.insertAudioRegion[I](timeline, time = Span(0L, numFramesTL),
      /* track = 0, */ audioCue = audioCueI, gOffset = 0L /* , bus = None */)

    val diff = Proc[I]
    val diffGr = SynthGraph {
      import synth._
      import ugen._
      val in0 = ScanIn(Proc.mainIn)
      // in0.poll(1, "audio-file-view")
      val in = if (audioCueV.numChannels == 1) Pan2.ar(in0) else in0  // XXX TODO
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

//    implicit val undoManager: UndoManager = UndoManager()
    // val offsetView  = LongSpinnerView  (grapheme.offset, "Offset")
    // val gainView    = DoubleSpinnerView[S](audioCue.value.gain /* RRR */, "Gain", width = 90)
    val res: Impl[S, I] = new Impl[S, I](/* gainView = gainView, */ inMemoryBridge = system.inMemoryTx) {
      val timelineModel = TimelineModel(bounds = fullSpanTL, visible = fullSpanTL, virtual = fullSpanTL,
        sampleRate = TimeRef.SampleRate)
      val holder: stm.Source[S#Tx, Obj[S]]  = tx.newHandle(obj0)
      val transportView: TransportView[I]   = TransportView[I](transport, timelineModel, hasMillis = true, hasLoop = true)
    }

    deferTx {
      res.guiInit(audioCueV)
    } (tx)
    res
  }

  private abstract class Impl[S <: Sys[S], I <: Sys[I]](/* gainView: View[S], */ inMemoryBridge: S#Tx => I#Tx)
                                                       (implicit val universe: Universe[S])
    extends AudioFileView[S] with ComponentHolder[Component] { impl =>

    type C = Component

    protected def holder       : stm.Source[S#Tx, AudioCue.Obj[S]]
    protected def transportView: TransportView[I]
    protected def timelineModel: TimelineModel

    private var _sonogram: sonogram.Overview = _

    def dispose()(implicit tx: S#Tx): Unit = {
      val itx: I#Tx = inMemoryBridge(tx)
      transportView.transport.dispose()(itx)
      transportView.dispose()(itx)
//      gainView     .dispose()
      deferTx {
        SonogramManager.release(_sonogram)
      }
    }

    def guiInit(snapshot: AudioCue): Unit = {
      // println("AudioFileView guiInit")
      _sonogram = SonogramManager.acquire(snapshot.artifact)
      // import SonogramManager.executionContext
      //      sono.onComplete {
      //        case x => println(s"<view> $x")
      //      }

      val sonogramView  = new ViewJ(_sonogram, timelineModel)
      val ggVisualBoost = GUI.boostRotary()(sonogramView.visualBoost = _)

      // val ggDragRegion = new DnD.Button(holder, snapshot, timelineModel)
      val ggDragRegion = new DragSourceButton() {
        protected def createTransferable(): Option[Transferable] = {
          val spOpt = timelineModel.selection match {
            case sp0: Span if sp0.nonEmpty => Some(sp0)
            case _ => timelineModel.bounds match {
              case sp0: Span => Some(sp0)
              case _ => None
            }
          }
          spOpt.map { sp =>
            val drag  = timeline.DnD.AudioDrag(universe, holder, selection = sp)
            val t     = DragAndDrop.Transferable(timeline.DnD.flavor)(drag)
            t
          }
        }
        tooltip = "Drag Selected Region"
      }

      val topPane = new BoxPanel(Orientation.Horizontal) {
        contents ++= Seq(
          HStrut(4),
          ggDragRegion,
          // new BusSinkButton[S](impl, ggDragRegion),
//          HStrut(4),
//          new Label("Gain:"),
//          gainView.component,
          HStrut(8),
          ggVisualBoost,
          HGlue,
          HStrut(4),
          transportView.component,
          HStrut(4)
        )
      }

      val pane = new BorderPanel {
        layoutManager.setVgap(2)
        add(topPane,                BorderPanel.Position.North )
        add(sonogramView.component, BorderPanel.Position.Center)
      }

      component = pane
      Util.setInitialFocus(sonogramView.canvasComponent)
    }

    def obj(implicit tx: S#Tx): AudioCue.Obj[S] = holder()
  }
}