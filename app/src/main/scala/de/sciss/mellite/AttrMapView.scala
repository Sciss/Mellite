/*
 *  AttrMapView.scala
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
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Txn => LTxn}
import de.sciss.mellite.impl.document.{AttrMapViewImpl => Impl}
import de.sciss.proc.Universe

object AttrMapView {
  def apply[T <: Txn[T]](obj: Obj[T])(implicit tx: T, universe: Universe[T],
                                      undoManager: UndoManager): AttrMapView[T] =
    Impl(obj)

  type Selection[T <: Txn[T]] = MapView.Selection[T]

  type Update[T <: Txn[T]] = MapView.Update[T, AttrMapView[T]]
  type SelectionChanged[T <: Txn[T]]  = MapView.SelectionChanged[T, AttrMapView[T]]
  val  SelectionChanged: MapView.SelectionChanged.type = MapView.SelectionChanged
}
trait AttrMapView[T <: LTxn[T]] extends MapView[T, AttrMapView[T]] with UniverseObjView[T] {
  override def obj(implicit tx: T): Obj.AttrMap[T]

  def receiver(implicit tx: T): Obj[T]
}