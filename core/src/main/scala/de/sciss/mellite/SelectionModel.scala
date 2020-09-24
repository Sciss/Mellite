/*
 *  SelectionModel.scala
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

import de.sciss.lucre.Txn
import de.sciss.mellite.impl.SelectionModelImpl
import de.sciss.model.Model

object SelectionModel {
  def apply[T <: Txn[T], Repr]: SelectionModel[T, Repr] =
    new SelectionModelImpl[T, Repr]

  type Listener[T <: Txn[T], Repr] = Model.Listener[Update[T, Repr]]
  final case class Update[T <: Txn[T], Repr](added: Set[Repr], removed: Set[Repr])
}
/** An observable unordered set of (visually selected) objects.
  */
trait SelectionModel[T <: Txn[T], Repr]
  extends Model[SelectionModel.Update[T, Repr]] {

  def contains(view: Repr): Boolean

  def +=(view: Repr): Unit
  def -=(view: Repr): Unit

  def clear(): Unit

  /** Since conceptually this is an unordered set, so do not rely
    * on a particular sequence in the `iterator`.
    */
  def iterator: Iterator[Repr]

  def isEmpty : Boolean
  def nonEmpty: Boolean
}