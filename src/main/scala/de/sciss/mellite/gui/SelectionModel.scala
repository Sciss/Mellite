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

package de.sciss.mellite.gui

import de.sciss.lucre.stm.Sys
import de.sciss.mellite.gui.impl.component.{SelectionModelImpl => Impl}
import de.sciss.model.Model

object SelectionModel {
  def apply[S <: Sys[S], Repr]: SelectionModel[S, Repr] = new Impl[S, Repr]

  type Listener[S <: Sys[S], Repr] = Model.Listener[Update[S, Repr]]
  final case class Update[S <: Sys[S], Repr](added: Set[Repr], removed: Set[Repr])
}
trait SelectionModel[S <: Sys[S], Repr]
  extends Model[SelectionModel.Update[S, Repr]] {

  def contains(view: Repr): Boolean
  def +=(view: Repr): Unit
  def -=(view: Repr): Unit
  def clear(): Unit
  def iterator: Iterator[Repr]
  def isEmpty : Boolean
  def nonEmpty: Boolean
}