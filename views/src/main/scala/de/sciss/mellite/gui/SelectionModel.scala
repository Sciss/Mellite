/*
 *  SelectionModel.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package mellite
package gui

import de.sciss.lucre.event.Sys
import de.sciss.model.Model
import impl.component.{SelectionModelImpl => Impl}

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
}