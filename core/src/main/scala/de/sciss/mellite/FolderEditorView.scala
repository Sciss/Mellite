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
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Folder
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.Universe

import scala.swing.{Action, Component, SequentialContainer}

object FolderEditorView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[S <: Sys[S]](folder: Folder[S])(implicit tx: S#Tx, universe: Universe[S],
                                              undoManager: UndoManager = UndoManager()): FolderEditorView[S]
  }

  def apply[S <: Sys[S]](folder: Folder[S])(implicit tx: S#Tx, universe: Universe[S],
                                            undoManager: UndoManager = UndoManager()): FolderEditorView[S] =
    companion(folder)
}
trait FolderEditorView[S <: stm.Sys[S]] extends View.Editable[S] with UniverseView[S] {
  def peer: FolderView[S]

  def bottomComponent: Component with SequentialContainer

  def actionDuplicate: Action
}