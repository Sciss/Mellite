/*
 *  TimelineViewBase.scala
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

import de.sciss.audiowidgets.TimelineModel
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.mellite.gui
import de.sciss.synth.proc.gui.UniverseView

import scala.swing.Action

/** Common base for `TimelineView` and `GraphemeView`. */
trait TimelineViewBase[S <: stm.Sys[S], Y, Child] extends UniverseView[S] with View.Editable[S] {
  def timelineModel   : TimelineModel
  def selectionModel  : gui.SelectionModel[S, Child]

  def canvas          : TimelineCanvas2D[S, Y, Child]

//  def transportView : TransportView  [S]

  // ---- GUI actions ----
  def actionSelectAll           : Action
  def actionSelectFollowing     : Action
  def actionDelete              : Action
}