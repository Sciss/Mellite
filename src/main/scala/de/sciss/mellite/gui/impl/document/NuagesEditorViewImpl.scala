/*
 *  NuagesEditorViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package document

import javax.swing.SpinnerNumberModel

import de.sciss.desktop.{FileDialog, OptionPane, PathField, UndoManager}
import de.sciss.{desktop, equal}
import de.sciss.file.File
import de.sciss.icons.raphael
import de.sciss.lucre.expr.{BooleanObj, IntVector}
import de.sciss.lucre.stm
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{Window, defer, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc
import de.sciss.nuages.{NamedBusConfig, Nuages, NuagesView, ScissProcs}
import de.sciss.swingplus.{GroupPanel, Separator, Spinner}
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.proc.{Folder, Workspace}

import scala.swing.Swing._
import scala.swing.{Action, BoxPanel, Button, Component, Dialog, Label, Orientation}

object NuagesEditorViewImpl {
  def apply[S <: Sys[S]](obj: Nuages[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                         cursor: stm.Cursor[S], undoManager: UndoManager): NuagesEditorView[S] = {
    val folder  = FolderView(obj.folder)
    val folder1 = new FolderFrameImpl.ViewImpl[S](folder)
    folder1.init()
    val res     = new Impl(tx.newHandle(obj), folder1)
    deferTx {
      res.guiInit()
    }
    res
  }

  private final class Impl[S <: Sys[S]](nuagesH: stm.Source[S#Tx, Nuages[S]],
                                        folderView: FolderFrameImpl.ViewImpl[S])
                                       (implicit val undoManager: UndoManager, val workspace: Workspace[S],
                                        val cursor: stm.Cursor[S])
    extends NuagesEditorView[S] with ComponentHolder[Component] {
    impl =>

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
      val numMasterChans  = Prefs.audioNumOutputs.getOrElse(Prefs.defaultAudioNumOutputs)
      val hpOffset        = Prefs.headphonesBus  .getOrElse(Prefs.defaultHeadphonesBus  )
      val nCfg            = Nuages.Config()
      val masterChans     = attr.$[IntVector](NuagesEditorView.attrMasterChans)
        .fold[Vec[Int]](0 until numMasterChans)(_.value)
      nCfg.masterChannels = Some(masterChans)
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
              val nameView = AttrCellView.name(nuages)
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

        def perform(genChans: Int, audioFilesFolder: Option[File]): Unit = cursor.step { implicit tx =>
          val n = nuagesH()
          Nuages.mkCategoryFolders(n)
          val nCfg = buildConfiguration()
          val sCfg = ScissProcs.Config()
          sCfg.generatorChannels  = genChans
          sCfg.audioFilesFolder   = audioFilesFolder
          // sCfg.masterGroups       = ...
          import Mellite.compiler
          val fut = ScissProcs.compileAndApply[S](n, nCfg, sCfg)
          fut.failed.foreach { ex =>
            defer {
              Dialog.showMessage(
                message     = s"Unable to compile processes\n\n${GUI.formatException(ex)}",
                title       = title,
                messageType = Dialog.Message.Error
              )
            }
          }
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
            perform(genChans = mGenChans.getNumber.intValue(), audioFilesFolder = ggAudioFiles.valueOption)
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

    private def openLive()(implicit tx: S#Tx): Option[Window[S]] = {
      import Mellite.auralSystem
      val n     = nuagesH()
      val nCfg  = buildConfiguration()
      val frame = new WindowImpl[S] {
        val view = NuagesView(n, nCfg)
        override val undecorated = true

        override protected def initGUI(): Unit = window.component match {
          case w: scala.swing.Window => view.installFullScreenKey(w)
          case _ =>
        }

        override protected def checkClose(): Boolean =
          cursor.step { implicit tx => !view.panel.transport.isPlaying }
      }
      frame.init()
      Some(frame)
    }
  }
}