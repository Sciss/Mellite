/*
 *  FolderEditorView.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.Folder
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.proc.Universe

import scala.swing.{Action, Component, SequentialContainer}

object FolderEditorView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: Txn[T]](folder: Folder[T])(implicit tx: T, universe: Universe[T],
                                              undoManager: UndoManager = UndoManager()): FolderEditorView[T]
  }

  def apply[T <: Txn[T]](folder: Folder[T])(implicit tx: T, universe: Universe[T],
                                            undoManager: UndoManager = UndoManager()): FolderEditorView[T] =
    companion(folder)
}
trait FolderEditorView[T <: LTxn[T]] extends View.Editable[T] with UniverseView[T] {
  def peer: FolderView[T]

  def bottomComponent: Component with SequentialContainer

  def actionDuplicate: Action
}