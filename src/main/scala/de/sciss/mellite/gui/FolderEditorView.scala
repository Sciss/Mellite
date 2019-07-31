/*
 *  FolderEditorView.scala
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

package de.sciss.mellite.gui

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Folder
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.UniverseView
import de.sciss.mellite.gui.impl.document.FolderEditorViewImpl
import de.sciss.synth.proc.Universe

import scala.swing.{Action, Component, SequentialContainer}

object FolderEditorView {
  def apply[S <: Sys[S]](folder: Folder[S])(implicit tx: S#Tx, universe: Universe[S],
                                            undoManager: UndoManager = UndoManager()): FolderEditorView[S] =
    FolderEditorViewImpl(folder)
}
trait FolderEditorView[S <: stm.Sys[S]] extends View.Editable[S] with UniverseView[S] {
  def peer: FolderView[S]

  def bottomComponent: Component with SequentialContainer

  def actionDuplicate: Action
}