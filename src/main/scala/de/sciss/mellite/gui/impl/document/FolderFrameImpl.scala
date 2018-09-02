/*
 *  FolderFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
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
package document

import de.sciss.desktop.{Menu, UndoManager}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Folder
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.Workspace

import scala.concurrent.Future
import scala.swing.{Action, Component, SequentialContainer}

object FolderFrameImpl {
  def apply[S <: Sys[S]](name: CellView[S#Tx, String],
                         folder: Folder[S],
                         isWorkspaceRoot: Boolean)(implicit tx: S#Tx,
                         workspace: Workspace[S], cursor: stm.Cursor[S]): FolderFrame[S] = {
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

//  private def addImportJSONAction[S <: Sys[S]](w: WindowImpl[S], action: Action): Unit =
//    Application.windowHandler.menuFactory.get("actions") match {
//      case Some(mEdit: Menu.Group) =>
//        val itDup = Menu.Item("import-json", action)
//        mEdit.add(Some(w.window), itDup)
//      case _ =>
//    }

  private final class FrameImpl[S <: Sys[S]](val view: FolderEditorView[S], name: CellView[S#Tx, String],
                                             isWorkspaceRoot: Boolean /* , interceptQuit: Boolean */)
    extends WindowImpl[S](name) with FolderFrame[S] /* with Veto[S#Tx] */ {

    def workspace : Workspace [S] = view.workspace
    def folderView: FolderView[S] = view.peer

    def bottomComponent: Component with SequentialContainer = view.bottomComponent

//    private[this] var quitAcceptor = Option.empty[() => Boolean]

    override protected def initGUI(): Unit = {
      addDuplicateAction (this, view.actionDuplicate)
      // addImportJSONAction(this, view.actionImportJSON)
      // if (interceptQuit) quitAcceptor = ... // Some(Desktop.addQuitAcceptor(checkClose()))
    }

    override protected def placement = WindowPlacement(0.5f, 0.0f)

//    override protected def checkClose(): Boolean = !interceptQuit ||
//      ActionCloseAllWorkspaces.check(workspace, Some(window))

//    override protected def performClose(): Future[Unit] =
//      if (isWorkspaceRoot) {
//        ActionCloseAllWorkspaces.close(workspace)
//      } else {
//        super.performClose()
//      }

//    override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
//      if      (!isWorkspaceRoot ) None
////      else if (interceptQuit    ) Some(this)
//      else ... // collectVetos()

    override protected def performClose(): Future[Unit] = if (!isWorkspaceRoot) super.performClose() else {
      log(s"Closing workspace ${workspace.folder}")
      ActionCloseAllWorkspaces.tryClose(workspace, Some(window))
    }

//    private def collectVetos()(implicit tx: S#Tx): Option[Veto[S#Tx]] = {
//      val list: List[Veto[S#Tx]] = workspace.dependents.flatMap {
//        case mv: DependentMayVeto[S#Tx] if mv != self => mv.prepareDisposal()
//      } (breakOut)
//
//      list match {
//        case Nil => None
//        case _ =>
//          val res = new Veto[S#Tx] {
//            def vetoMessage(implicit tx: S#Tx): String =
//              list.map(_.vetoMessage).mkString("\n")
//
//            def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] = ...
//          }
//          Some(res)
//      }
//    }

//    private def vetoMessageNothing = "Nothing to veto."
//
//    def vetoMessage(implicit tx: S#Tx): String =
//      if      (!isWorkspaceRoot ) vetoMessageNothing
//      else if (interceptQuit    ) ActionCloseAllWorkspaces.messageClosingInMemory
//      else collectVetos() match {
//        case None     => vetoMessageNothing
//        case Some(v)  => v.vetoMessage
//      }
//
//    /** Attempts to resolve the veto condition by consulting the user.
//      *
//      * @return successful future if the situation is resolved, e.g. the user agrees to
//      *         proceed with the operation. failed future if the veto is upheld, and
//      *         the caller should abort the operation.
//      */
//    def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] = ...
//
//    override def dispose()(implicit tx: S#Tx): Unit = if (!wasDisposed) {
//      super.dispose()
////      if (isWorkspaceRoot) ActionCloseAllWorkspaces.close(workspace)
//      deferTx {
//        quitAcceptor.foreach(Desktop.removeQuitAcceptor)
//      }
//    }
  }
}