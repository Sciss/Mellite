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