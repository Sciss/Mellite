/*
 *  FScapeOutputsView.scala
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

package de.sciss.mellite

import de.sciss.desktop.UndoManager
import de.sciss.proc.FScape
import de.sciss.lucre.Source
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.mellite.impl.fscape.FScapeOutputsViewImpl
import de.sciss.proc.Universe

object FScapeOutputsView {
  final case class Drag[T <: Txn[T]](universe: Universe[T], fscape: Source[T, FScape[T]], key: String)

  final val flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  def apply[T <: Txn[T]](obj: FScape[T])(implicit tx: T, universe: Universe[T],
                                         undoManager: UndoManager): FScapeOutputsView[T] =
    FScapeOutputsViewImpl(obj)
}
trait FScapeOutputsView[T <: Txn[T]] extends UniverseObjView[T] with View.Editable[T] {
  override def obj(implicit tx: T): FScape[T]
}