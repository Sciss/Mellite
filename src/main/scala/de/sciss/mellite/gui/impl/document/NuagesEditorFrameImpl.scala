/*
 *  NuagesEditorFrameImpl.scala
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

import de.sciss.desktop.impl.UndoManagerImpl
import de.sciss.lucre.stm
import de.sciss.lucre.swing.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Workspace

object NuagesEditorFrameImpl {
  def apply[S <: Sys[S]](obj: Nuages[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): NuagesEditorFrame[S] = {
    implicit val undo = new UndoManagerImpl
    val view          = NuagesEditorView(obj)
    val name          = AttrCellView.name(obj)
    val res           = new FrameImpl[S](view, name)
    res.init()
    res
  }

  private final class FrameImpl[S <: Sys[S]](val view: NuagesEditorView[S], name: CellView[S#Tx, String])
    extends WindowImpl[S](name) with NuagesEditorFrame[S] {

    override protected def initGUI(): Unit = {
      FolderFrameImpl.addDuplicateAction(this, view.actionDuplicate) // XXX TODO -- all hackish
    }
  }
}