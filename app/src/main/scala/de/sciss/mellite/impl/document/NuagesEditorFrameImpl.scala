/*
 *  NuagesEditorFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{NuagesEditorFrame, NuagesEditorView}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.nuages.Nuages
import de.sciss.proc.Universe

object NuagesEditorFrameImpl {
  def apply[T <: Txn[T]](obj: Nuages[T])
                        (implicit tx: T, universe: Universe[T]): NuagesEditorFrame[T] = {
    implicit val undo: UndoManager = UndoManager()
    val view          = NuagesEditorView(obj)
    val name          = CellView.name(obj)
    val res           = new FrameImpl[T](view, name)
    res.init()
    res
  }

  private final class FrameImpl[T <: Txn[T]](val view: NuagesEditorView[T], name: CellView[T, String])
    extends WindowImpl[T](name) with NuagesEditorFrame[T] {

    override protected def initGUI(): Unit = {
      FolderFrameImpl.addDuplicateAction(this, view.actionDuplicate) // XXX TODO -- all hackish
    }
  }
}