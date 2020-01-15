/*
 *  NuagesEditorFrameImpl.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{NuagesEditorFrame, NuagesEditorView}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Universe

object NuagesEditorFrameImpl {
  def apply[S <: Sys[S]](obj: Nuages[S])
                        (implicit tx: S#Tx, universe: Universe[S]): NuagesEditorFrame[S] = {
    implicit val undo: UndoManager = UndoManager()
    val view          = NuagesEditorView(obj)
    val name          = CellView.name(obj)
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