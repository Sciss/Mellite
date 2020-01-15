/*
 *  GraphemeView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Grapheme, Universe}

object GraphemeView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[S <: Sys[S]](gr: Grapheme[S])
                          (implicit tx: S#Tx, universe: Universe[S],
                           undoManager: UndoManager): GraphemeView[S]
  }

  def apply[S <: Sys[S]](gr: Grapheme[S])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undoManager: UndoManager): GraphemeView[S] =
    companion(gr)

  sealed trait Mode
  object Mode {
    case object OneDim extends Mode
    case object TwoDim extends Mode
  }
}
trait GraphemeView[S <: stm.Sys[S]] extends TimelineViewBase[S, Double, ObjGraphemeView[S]] {
  def graphemeH: stm.Source[S#Tx, Grapheme[S]]
  def grapheme(implicit tx: S#Tx): Grapheme[S]

  override def canvas: GraphemeCanvas[S]
}