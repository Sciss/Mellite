/*
 *  FolderFrameImpl.scala
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

package de.sciss.mellite.gui.impl.document

import de.sciss.desktop.{Menu, UndoManager}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm.Folder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{Application, FolderEditorView, FolderView, WindowPlacement}
import de.sciss.mellite.gui.{ActionCloseAllWorkspaces, FolderFrame}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.synth.proc.Universe

import scala.concurrent.Future
import scala.swing.{Action, Component, SequentialContainer}

object FolderFrameImpl {
  def apply[S <: Sys[S]](name: CellView[S#Tx, String],
                         folder: Folder[S],
                         isWorkspaceRoot: Boolean)(implicit tx: S#Tx,
                                                   universe: Universe[S]): FolderFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val view  = FolderEditorView(folder)
    val res   = new FrameImpl[S](view, name = name, isWorkspaceRoot = isWorkspaceRoot /* , interceptQuit = interceptQuit */)
    res.init()
    res
  }

  def addDuplicateAction[S <: Sys[S]](w: WindowImpl[S], action: Action): Unit =
    Application.windowHandler.menuFactory.get("edit") match {
      case Some(mEdit: Menu.Group) =>
        val itDup = Menu.Item("duplicate", action)
        mEdit.add(Some(w.window), itDup)    // XXX TODO - should be insert-after "Select All"
      case _ =>
    }

  private final class FrameImpl[S <: Sys[S]](val view: FolderEditorView[S], name: CellView[S#Tx, String],
                                             isWorkspaceRoot: Boolean /* , interceptQuit: Boolean */)
    extends WindowImpl[S](name) with FolderFrame[S] /* with Veto[S#Tx] */ {

//    def workspace : Workspace [S] = view.workspace
    def folderView: FolderView[S] = view.peer

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