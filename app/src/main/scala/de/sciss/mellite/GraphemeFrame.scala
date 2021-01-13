/*
 *  GraphemeFrame.scala
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

import de.sciss.lucre
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.grapheme.{GraphemeFrameImpl => Impl}
import de.sciss.proc.{Grapheme, Universe}

object GraphemeFrame {
  def apply[T <: Txn[T]](group: Grapheme[T])
                        (implicit tx: T, universe: Universe[T]): GraphemeFrame[T] =
    Impl(group)
}
trait GraphemeFrame[T <: LTxn[T]] extends lucre.swing.Window[T] {
  def view: GraphemeView[T]
}