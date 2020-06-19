/*
 *  NuagesEditorViewImpl.scala
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

package de.sciss.mellite.impl.document

import de.sciss.desktop.{FileDialog, OptionPane, PathField, UndoManager}
import de.sciss.file.File
import de.sciss.icons.raphael
import de.sciss.lucre.expr.{BooleanObj, CellView, IntVector}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Folder
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.Window
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{FolderEditorView, GUI, Prefs, Veto}
import de.sciss.mellite.{ActionBounce, FolderFrame, NuagesEditorView, TimelineFrame}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.nuages.{NamedBusConfig, Nuages, NuagesView, ScissProcs}
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.{GroupPanel, Separator, Spinner}
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe
import de.sciss.{desktop, equal}
import javax.swing.SpinnerNumberModel

import scala.concurrent.Future
import scala.swing.Swing._
import scala.swing.{Action, BoxPanel, Button, Component, Dialog, Label, Orientation}

object NuagesEditorViewImpl {
  def apply[S <: Sys[S]](obj: Nuages[S])(implicit tx: S#Tx, universe: Universe[S], undoManager: UndoManager): NuagesEditorView[S] = {
    val folder  = FolderEditorView[S](obj.folder)
    val res     = new Impl[S](tx.newHandle(obj), folder)
    deferTx {
      res.guiInit()
    }
    res
  }

  private final class Impl[S <: Sys[S]](nuagesH: stm.Source[S#Tx, Nuages[S]],
                                        folderView: FolderEditorView[S])
    extends NuagesEditorView[S] with ComponentHolder[Component] {
    impl =>

    type C = Component

    implicit val universe: Universe[S] = folderView.universe

    def undoManager: UndoManager = folderView.undoManager

    def actionDuplicate: Action = folderView.actionDuplicate

    private def buildConfiguration()(implicit tx: S#Tx): Nuages.ConfigBuilder = {
      val n               = nuagesH()
      val attr            = n.attr

      def mkBusConfigs(key: String): Vec[NamedBusConfig] =
        attr.$[Folder](key).fold(Vec.empty[NamedBusConfig]) { f =>
          f.iterator.collect {
            case i: IntVector[S] =>
              import proc.Implicits._
              val name    = i.name
              val indices = i.value
              NamedBusConfig(name, indices)
          } .toIndexedSeq
        }

      val hasSolo         = attr.$[BooleanObj](NuagesEditorView.attrUseSolo).exists(_.value)
      val numMainChans    = Prefs.audioNumOutputs.getOrElse(Prefs.defaultAudioNumOutputs)
      val hpOffset        = Prefs.headphonesBus  .getOrElse(Prefs.defaultHeadphonesBus  )
      val nCfg            = Nuages.Config()
      val mainChans       = attr.$[IntVector](NuagesEditorView.attrMainChans)
        .fold[Vec[Int]](0 until numMainChans)(_.value)
      nCfg.mainChannels   = Some(mainChans)
      nCfg.soloChannels   = if (!hasSolo) None else Some(hpOffset to (hpOffset + 1))
      nCfg.micInputs      = mkBusConfigs(NuagesEditorView.attrMicInputs  )
      nCfg.lineInputs     = mkBusConfigs(NuagesEditorView.attrLineInputs )
      nCfg.lineOutputs    = mkBusConfigs(NuagesEditorView.attrLineOutputs)
      nCfg
    }

    def guiInit(): Unit = {
      val ggPower = Button("Live!") {
        impl.cursor.step { implicit tx => openLive() }
      }
      ggPower.tooltip = "Open the live performance interface"
      val shpPower = raphael.Shapes.Power _
      ggPower.icon          = GUI.iconNormal  (shpPower)
      ggPower.disabledIcon  = GUI.iconDisabled(shpPower)

      val ggClearTL = Button("Clear…") {
        val opt = OptionPane("Clearing the timeline cannot be undone!\nAre you sure?", OptionPane.Options.YesNo,
          OptionPane.Message.Warning)
        val res = opt.show(desktop.Window.find(component), title = "Clear Nuages Timeline")
        import equal.Implicits._
        if (res === OptionPane.Result.Yes)
          cursor.step { implicit tx =>
            nuagesH().surface match {
              case Nuages.Surface.Timeline(tl) =>
                tl.modifiableOption.foreach { tlMod =>
                  tlMod.clear() // XXX TODO -- use undo manager?
                }
              case Nuages.Surface.Folder(f) =>
                f.clear() // XXX TODO -- use undo manager?
            }
          }
      }
      ggClearTL.tooltip = "Erase objects created during the previous live session from the timeline"

      val ggViewTL = Button("View") {
        cursor.step { implicit tx =>
          val nuages = nuagesH()
          nuages.surface match {
            case Nuages.Surface.Timeline(tl) =>
              TimelineFrame(tl)
            case Nuages.Surface.Folder(f) =>
              val nameView = CellView.name(nuages)
              FolderFrame(nameView, f)
          }
        }
      }

      val ggPopulate = Button("Populate…") {
        val isEmpty = cursor.step { implicit tx =>
          val n = nuagesH()
          n.generators.forall(_.isEmpty) &&
          n.filters   .forall(_.isEmpty) &&
          n.collectors.forall(_.isEmpty)
        }

        val title = "Populate Nuages Timeline"

        def showPane[A](opt: OptionPane[A]): A =
          opt.show(desktop.Window.find(component), title = title)

        def perform(genNumChannels: Int, audioFilesFolder: Option[File]): Unit = cursor.step { implicit tx =>
          val n = nuagesH()
          Nuages.mkCategoryFolders(n)
          val nCfg = buildConfiguration()
          val sCfg = ScissProcs.Config()
          sCfg.genNumChannels     = genNumChannels
          sCfg.audioFilesFolder   = audioFilesFolder
          // sCfg.mainGroups       = ...
          ScissProcs[S](n, nCfg, sCfg)
        }

        import equal.Implicits._

        def configureAndPerform(): Unit = {
          val lbGenChans    = new Label("Generator Channels:")
          val mGenChans     = new SpinnerNumberModel(0, 0, 256, 1)
          val ggGenChans    = new Spinner(mGenChans)
          ggGenChans.tooltip = "Generators and filters will use this many channels\n(0 for no forced expansion)"
          val lbAudioFiles  = new Label("Folder of Audio Files:")
          val ggAudioFiles  = new PathField
          // XXX TODO PathField.tooltip doesn't work
          ggAudioFiles.tooltip = "For any audio file within this folder,\na player process will be created"
          ggAudioFiles.mode = FileDialog.Folder

          val box = new GroupPanel {
            horizontal  = Seq(Par(Trailing)(lbGenChans, lbAudioFiles), Par(ggGenChans, ggAudioFiles))
            vertical    = Seq(
              Par(Baseline)(lbGenChans  , ggGenChans  ),
              Par(Baseline)(lbAudioFiles, ggAudioFiles))
          }

          val pane = desktop.OptionPane.confirmation(box, optionType = Dialog.Options.OkCancel,
            messageType = Dialog.Message.Question, focus = Some(ggGenChans))
          val res = showPane(pane)
          if (res === OptionPane.Result.Yes) {
            perform(genNumChannels = mGenChans.getNumber.intValue(), audioFilesFolder = ggAudioFiles.valueOption)
          }
        }

        if (isEmpty) configureAndPerform()
        else {
          val opt = OptionPane("Folders seem to be populated already!\nAre you sure?", OptionPane.Options.YesNo,
            OptionPane.Message.Warning)
          val res = showPane(opt)
          if (res === OptionPane.Result.Yes) configureAndPerform()
        }
      }
      ggPopulate.tooltip = "Add standard sound processes (generators, filters, collectors)"

      component = new BoxPanel(Orientation.Vertical) {
        contents += folderView.component
        contents += Separator()
        contents += VStrut(2)
        contents += new BoxPanel(Orientation.Horizontal) {
          contents += ggPower
          contents += HStrut(32)
          contents += new Label("Timeline:")
          contents += ggClearTL
          contents += ggViewTL
          contents += HStrut(32)
          contents += ggPopulate
        }
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = folderView.dispose()

    object actionBounce extends ActionBounce(this, nuagesH) {
      import ActionBounce._

      override protected def spanPresets(): SpanPresets = {
        cursor.step { implicit tx =>
          nuagesH().surface match {
            case Nuages.Surface.Timeline(tl)  => presetAllTimeline(tl)
            case Nuages.Surface.Folder(_)     => Nil
          }
        }
      }
    }

    private def openLive()(implicit tx: S#Tx): Option[Window[S]] = {
      val n     = nuagesH()
      val nCfg  = buildConfiguration()
      val frame: WindowImpl[S] = new WindowImpl[S] with Veto[S#Tx] {
        val view = NuagesView(n, nCfg)
        override val undecorated = true

        override protected def initGUI(): Unit =
          view.installFullScreenKey(window.component)

        override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
          if (!view.panel.transport.isPlaying) None else Some(this)

        def vetoMessage(implicit tx: S#Tx): String = "Cannot close a running performance."

        def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] =
          Future.failed(Aborted())
      }
      frame.init()
      Some(frame)
    }
  }
}