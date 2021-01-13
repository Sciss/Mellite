/*
 *  MapView.scala
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

import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.model.Model

object MapView {
  type Selection[T <: LTxn[T]] = List[(String, ObjView[T])]

  sealed trait Update[T <: LTxn[T], Repr] { def view: Repr }
  final case class SelectionChanged[T <: LTxn[T], Repr](view: Repr, selection: Selection[T])
    extends Update[T, Repr]
}
trait MapView[T <: LTxn[T], Repr]
  extends UniverseView[T] with View.Editable[T] with Model[MapView.Update[T, Repr]] {

  def selection: MapView.Selection[T]

  def queryKey(initial: String = "key"): Option[String]
}