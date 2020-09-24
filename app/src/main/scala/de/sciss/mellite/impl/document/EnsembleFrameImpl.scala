/*
 *  EnsembleFrameImpl.scala
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
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.EnsembleFrame
import de.sciss.mellite.impl.WindowImpl
import de.sciss.synth.proc.{Ensemble, Universe}

object EnsembleFrameImpl {
  def apply[T <: Txn[T]](obj: Ensemble[T])
                        (implicit tx: T, universe: Universe[T]): EnsembleFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val ensembleView      = EnsembleViewImpl(obj)
    val name  = CellView.name(obj)
    val res   = new FrameImpl[T](ensembleView, name)
    res.init()
    res
  }

  private final class FrameImpl[T <: Txn[T]](val ensembleView: EnsembleViewImpl.Impl[T], name: CellView[T, String])
    extends WindowImpl[T](name) with EnsembleFrame[T] {

    def view: View[T] = ensembleView

    override protected def initGUI(): Unit = {
      FolderFrameImpl.addDuplicateAction(this, ensembleView.view.actionDuplicate) // XXX TODO -- all hackish
    }
  }
}