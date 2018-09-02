/*
 *  GraphemeCanvas.scala
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

package de.sciss.mellite.gui

import de.sciss.audiowidgets.TimelineCanvas
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Grapheme

trait GraphemeCanvas[S <: Sys[S]] extends TimelineCanvas {
  def grapheme(implicit tx: S#Tx): Grapheme[S]

  def selectionModel: GraphemeObjView.SelectionModel[S]

  def screenYToModel(y: Int   ): Double
  def modelToScreenY(m: Double): Int
}