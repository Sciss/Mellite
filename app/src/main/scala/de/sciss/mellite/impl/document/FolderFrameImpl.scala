/*
 *  FolderFrameImpl.scala
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

package de.sciss.mellite.impl.document

import de.sciss.desktop.{FileDialog, Menu, OptionPane, UndoManager}
import de.sciss.equal.Implicits._
import de.sciss.file.File
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.store.InMemoryDB
import de.sciss.lucre.synth.{Txn => STxn}
import de.sciss.lucre.{Copy, Folder, Txn}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{ActionCloseAllWorkspaces, Application, FolderEditorView, FolderFrame, FolderView, Mellite, WindowPlacement}
import de.sciss.proc
import de.sciss.proc.{Durable, Universe, Workspace}

import java.io.{DataOutputStream, FileOutputStream}
import scala.concurrent.Future
import scala.swing.{Action, Component, SequentialContainer}

object FolderFrameImpl {
  def apply[T <: STxn[T]](name: CellView[T, String],
                         folder: Folder[T],
                         isWorkspaceRoot: Boolean)(implicit tx: T,
                                                   universe: Universe[T]): FolderFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val view  = FolderEditorView(folder)
    val res   = new FrameImpl[T](view, name = name, isWorkspaceRoot = isWorkspaceRoot /* , interceptQuit = interceptQuit */)
    res.init()
    res
  }

  def addDuplicateAction[T <: STxn[T]](w: WindowImpl[T], action: Action): Unit =
    Application.windowHandler.menuFactory.get("edit") match {
      case Some(mEdit: Menu.Group) =>
        val itDup = Menu.Item("duplicate", action)
        mEdit.add(Some(w.window), itDup)    // XXX TODO - should be insert-after "Select All"
      case _ =>
    }

  private final class FrameImpl[T <: STxn[T]](val view: FolderEditorView[T], name: CellView[T, String],
                                             isWorkspaceRoot: Boolean /* , interceptQuit: Boolean */)
    extends WindowImpl[T](name) with FolderFrame[T] /* with Veto[T] */ {

//    def workspace : Workspace [T] = view.workspace
    def folderView: FolderView[T] = view.peer

    def bottomComponent: Component with SequentialContainer = view.bottomComponent

//    private[this] var quitAcceptor = Option.empty[() => Boolean]

    private object actionExportBinaryWorkspace extends scala.swing.Action("Export Binary Workspace...") {
      private def selectFile(): Option[File] = {
        val fileDlg = FileDialog.save(title = "Binary Workspace File")
        fileDlg.show(Some(window)).flatMap { file0 =>
          import de.sciss.file._
          val name  = file0.name
          val file  = if (file0.ext.toLowerCase == s"${proc.Workspace.ext}.bin")
            file0
          else
            file0.parent / s"$name.${proc.Workspace.ext}.bin"

          if (!file.exists()) Some(file) else {
            val optOvr = OptionPane.confirmation(
              message     = s"File ${file.path} already exists.\nAre you sure you want to overwrite it?",
              optionType  = OptionPane.Options.OkCancel,
              messageType = OptionPane.Message.Warning
            )
            val fullTitle = "Export Binary Workspace"
            optOvr.title = fullTitle
            val resOvr = optOvr.show()
            val isOk = resOvr === OptionPane.Result.Ok

            if (!isOk) None else if (file.delete()) Some(file) else {
              val optUnable = OptionPane.message(
                message     = s"Unable to delete existing file ${file.path}",
                messageType = OptionPane.Message.Error
              )
              optUnable.title = fullTitle
              optUnable.show()
              None
            }
          }
        }
      }

      override def apply(): Unit =
        selectFile().foreach { f =>
          import de.sciss.file._
          type Tmp  = Durable.Txn
          val tmpDb = InMemoryDB(f.base)
          implicit val tmp: Durable = Durable(tmpDb)
          val ws = Workspace.Ephemeral[Tmp, Durable](tmp)  // initialized access

          val blob  = Txn.copy[T, Tmp, Array[Byte]] { (txIn0: T, txOut: Tmp) => {
            implicit val txIn: T = txIn0
            val context = Copy[T, Tmp]()(txIn, txOut)
            val fIn     = view.peer.root()
            val fOut    = ws.root(txOut)
            fIn.iterator.foreach { in =>
              val out = context(in)
              fOut.addLast(out)(txOut)
            }
            context.finish()
            tmpDb.toByteArray
          }} (view.cursor, tmp)

          // println(s"blob size = ${blob.length}")
          val fOut = new FileOutputStream(f)
          try {
            val dOut = new DataOutputStream(fOut)
            val BIN_COOKIE = 0x6d6c6c742d777300L  // "mllt-ws\u0000"
            dOut.writeLong(BIN_COOKIE)
            val mVer = Mellite.version
            dOut.writeUTF(mVer)
            dOut.write(blob)
            dOut.flush()
          } finally {
            fOut.close()
          }
        }
    }

    override protected def initGUI(): Unit = {
      addDuplicateAction (this, view.actionDuplicate)
      if (isWorkspaceRoot) {
        val mf = window.handler.menuFactory
//        bindMenus("file.bounce" -> actionExportBinaryWorkspace)
        mf.get("file.bounce") match {
          case Some(it: Menu.ItemLike[_]) =>
            it.bind(window, actionExportBinaryWorkspace)
          case None =>
        }
      }
    }

    override protected def placement: WindowPlacement = WindowPlacement(0.5f, 0.0f)

    override protected def performClose(): Future[Unit] = if (!isWorkspaceRoot) super.performClose() else {
      import view.universe.workspace
      ActionCloseAllWorkspaces.tryClose(workspace, Some(window))
    }
  }
}