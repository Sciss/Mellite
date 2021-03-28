/*
 *  TimelineViewBase.scala
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

import de.sciss.audiowidgets.TimelineModel
import de.sciss.lucre.Txn
import de.sciss.lucre.swing.View

import scala.swing.Action

/** Common base for `TimelineView` and `GraphemeView`. */
trait TimelineViewBase[T <: Txn[T], Y, Child] extends UniverseObjView[T] with View.Editable[T] {
  def timelineModel   : TimelineModel
  def selectionModel  : SelectionModel[T, Child]

  def canvas          : TimelineCanvas2D[T, Y, Child]

//  def transportView : TransportView  [T]

  // ---- GUI actions ----
  def actionSelectAll           : Action
  def actionSelectFollowing     : Action
  def actionDelete              : Action
}