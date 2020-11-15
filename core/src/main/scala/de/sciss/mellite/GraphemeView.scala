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
import de.sciss.lucre.Source
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.proc.{Grapheme, Universe}

object GraphemeView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: Txn[T]](gr: Grapheme[T])
                          (implicit tx: T, universe: Universe[T],
                           undoManager: UndoManager): GraphemeView[T]
  }

  def apply[T <: Txn[T]](gr: Grapheme[T])
                        (implicit tx: T, universe: Universe[T],
                         undoManager: UndoManager): GraphemeView[T] =
    companion(gr)

  sealed trait Mode
  object Mode {
    case object OneDim extends Mode
    case object TwoDim extends Mode
  }
}
trait GraphemeView[T <: LTxn[T]] extends TimelineViewBase[T, Double, ObjGraphemeView[T]] {
  def graphemeH: Source[T, Grapheme[T]]
  def grapheme(implicit tx: T): Grapheme[T]

  override def canvas: GraphemeCanvas[T]
}