/*
 *  SelectionModelImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl.component

import de.sciss.lucre.stm.Sys
import de.sciss.model.impl.ModelImpl

final class SelectionModelImpl[S <: Sys[S], Repr]
  extends SelectionModel[S, Repr] with ModelImpl[SelectionModel.Update[S, Repr]] {

  import SelectionModel.Update

  // no sync because we assume the model is only used on the event thread
  private[this] var set = Set.empty[Repr]

  def contains(view: Repr): Boolean = set.contains(view)

  def iterator: Iterator[Repr] = set.iterator

  def isEmpty : Boolean = set.isEmpty
  def nonEmpty: Boolean = set.nonEmpty

  def +=(view: Repr): Unit =
    if (!set.contains(view)) {
      set += view
      dispatch(Update(added = Set(view), removed = Set.empty))
    }

  def -=(view: Repr): Unit =
    if (set.contains(view)) {
      set -= view
      dispatch(Update(added = Set.empty, removed = Set(view)))
    }

  def clear(): Unit =
    if (set.nonEmpty) {
      val removed = set
      set = Set.empty
      dispatch(Update(added = Set.empty, removed = removed))
    }
}