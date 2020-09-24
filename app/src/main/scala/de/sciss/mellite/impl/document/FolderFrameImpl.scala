/*
 *  FolderFrameImpl.scala
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

import de.sciss.desktop.{Menu, UndoManager}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.Folder
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{Application, FolderEditorView, FolderView, WindowPlacement}
import de.sciss.mellite.{ActionCloseAllWorkspaces, FolderFrame}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.synth.proc.Universe

import scala.concurrent.Future
import scala.swing.{Action, Component, SequentialContainer}

object FolderFrameImpl {
  def apply[T <: Txn[T]](name: CellView[T, String],
                         folder: Folder[T],
                         isWorkspaceRoot: Boolean)(implicit tx: T,
                                                   universe: Universe[T]): FolderFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val view  = FolderEditorView(folder)
    val res   = new FrameImpl[T](view, name = name, isWorkspaceRoot = isWorkspaceRoot /* , interceptQuit = interceptQuit */)
    res.init()
    res
  }

  def addDuplicateAction[T <: Txn[T]](w: WindowImpl[T], action: Action): Unit =
    Application.windowHandler.menuFactory.get("edit") match {
      case Some(mEdit: Menu.Group) =>
        val itDup = Menu.Item("duplicate", action)
        mEdit.add(Some(w.window), itDup)    // XXX TODO - should be insert-after "Select All"
      case _ =>
    }

  private final class FrameImpl[T <: Txn[T]](val view: FolderEditorView[T], name: CellView[T, String],
                                             isWorkspaceRoot: Boolean /* , interceptQuit: Boolean */)
    extends WindowImpl[T](name) with FolderFrame[T] /* with Veto[T] */ {

//    def workspace : Workspace [T] = view.workspace
    def folderView: FolderView[T] = view.peer

    def bottomComponent: Component with SequentialContainer = view.bottomComponent

//    private[this] var quitAcceptor = Option.empty[() => Boolean]

    override protected def initGUI(): Unit = {
      addDuplicateAction (this, view.actionDuplicate)
      // addImportJSONAction(this, view.actionImportJSON)
      // if (interceptQuit) quitAcceptor = ... // Some(Desktop.addQuitAcceptor(checkClose()))
    }

    override protected def placement = WindowPlacement(0.5f, 0.0f)

    override protected def performClose(): Future[Unit] = if (!isWorkspaceRoot) super.performClose() else {
      import view.universe.workspace
      ActionCloseAllWorkspaces.tryClose(workspace, Some(window))
    }
  }
}