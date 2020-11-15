/*
 *  FolderFrame.scala
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

package de.sciss.mellite

import de.sciss.lucre
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.Folder
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.document.{FolderFrameImpl => Impl}
import de.sciss.proc.Universe

import scala.swing.{Component, SequentialContainer}

object FolderFrame {
  /** Creates a new frame for a folder view.
    *
    * @param name             optional window name
    * @param isWorkspaceRoot  if `true`, closes the workspace when the window closes; if `false` does nothing
    *                         upon closing the window
    */
  def apply[T <: Txn[T]](name: CellView[T, String], isWorkspaceRoot: Boolean)
                        (implicit tx: T, universe: Universe[T]): FolderFrame[T] =
    Impl(name = name, folder = universe.workspace.root, isWorkspaceRoot = isWorkspaceRoot)

  def apply[T <: Txn[T]](name: CellView[T, String], folder: Folder[T])
                        (implicit tx: T, universe: Universe[T]): FolderFrame[T] = {
    Impl(name = name, folder = folder, isWorkspaceRoot = false)
  }
}

trait FolderFrame[T <: Txn[T]] extends lucre.swing.Window[T] {
  override def view: FolderEditorView[T]

  def folderView: FolderView[T]

  def bottomComponent: Component with SequentialContainer
}