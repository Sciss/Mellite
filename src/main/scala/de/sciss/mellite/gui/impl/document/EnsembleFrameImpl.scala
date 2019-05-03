/*
 *  EnsembleFrameImpl.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.EnsembleFrame
import de.sciss.mellite.gui.impl.WindowImpl
import de.sciss.synth.proc.{Ensemble, Universe}

object EnsembleFrameImpl {
  def apply[S <: Sys[S]](obj: Ensemble[S])
                        (implicit tx: S#Tx, universe: Universe[S]): EnsembleFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val ensembleView      = EnsembleViewImpl(obj)
    val name  = CellView.name(obj)
    val res   = new FrameImpl[S](ensembleView, name)
    res.init()
    res
  }

  private final class FrameImpl[S <: Sys[S]](val ensembleView: EnsembleViewImpl.Impl[S], name: CellView[S#Tx, String])
    extends WindowImpl[S](name) with EnsembleFrame[S] {

    def view: View[S] = ensembleView

    override protected def initGUI(): Unit = {
      FolderFrameImpl.addDuplicateAction(this, ensembleView.view.actionDuplicate) // XXX TODO -- all hackish
    }
  }
}