/*
 *  GraphemeView.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.grapheme.{GraphemeViewImpl => Impl}
import de.sciss.synth.proc.{Grapheme, Universe}

object GraphemeView {
  def apply[S <: Sys[S]](group: Grapheme[S])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undoManager: UndoManager): GraphemeView[S] =
    Impl[S](group)

  sealed trait Mode
  object Mode {
    case object OneDim extends Mode
    case object TwoDim extends Mode
  }
}
trait GraphemeView[S <: stm.Sys[S]] extends TimelineViewBase[S, Double, GraphemeObjView[S]] {
  def graphemeH: stm.Source[S#Tx, Grapheme[S]]
  def grapheme(implicit tx: S#Tx): Grapheme[S]

  override def canvas: GraphemeCanvas[S]
}