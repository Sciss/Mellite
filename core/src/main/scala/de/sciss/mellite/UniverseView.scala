/*
 *  UniverseView.scala
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

import de.sciss.lucre.{Cursor, Obj, Txn}
import de.sciss.lucre.swing.View
import de.sciss.proc.Universe

trait UniverseView[T <: Txn[T]] extends View.Cursor[T] {
  implicit val universe: Universe[T]

  implicit def cursor: Cursor[T] = universe.cursor

  // on EDT
  def viewState: Set[ViewState]
}

trait UniverseObjView[T <: Txn[T]] extends UniverseView[T] {
  def obj(implicit tx: T): Obj[T]
}