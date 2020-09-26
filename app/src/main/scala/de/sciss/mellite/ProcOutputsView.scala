/*
 *  ProcOutputsView.scala
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
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.mellite.impl.proc.OutputsViewImpl
import de.sciss.synth.proc.{Proc, Universe}

object ProcOutputsView {
  final case class Drag[T <: Txn[T]](universe: Universe[T], proc: Source[T, Proc[T]], key: String)

  final val flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  def apply[T <: Txn[T]](obj: Proc[T])(implicit tx: T, universe: Universe[T],
                                       undoManager: UndoManager): ProcOutputsView[T] =
    OutputsViewImpl(obj)
}
trait ProcOutputsView[T <: Txn[T]] extends UniverseView[T] with View.Editable[T]