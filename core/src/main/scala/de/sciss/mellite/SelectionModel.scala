/*
 *  SelectionModel.scala
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

package de.sciss.mellite

import de.sciss.lucre.stm.Sys
import de.sciss.mellite.impl.SelectionModelImpl
import de.sciss.model.Model

object SelectionModel {
  def apply[S <: Sys[S], Repr]: SelectionModel[S, Repr] =
    new SelectionModelImpl[S, Repr]

  type Listener[S <: Sys[S], Repr] = Model.Listener[Update[S, Repr]]
  final case class Update[S <: Sys[S], Repr](added: Set[Repr], removed: Set[Repr])
}
/** An observable unordered set of (visually selected) objects.
  */
trait SelectionModel[S <: Sys[S], Repr]
  extends Model[SelectionModel.Update[S, Repr]] {

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