/*
 *  TimelineFrame.scala
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
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.timeline.{TimelineFrameImpl => Impl}
import de.sciss.proc.{Timeline, Universe}

object TimelineFrame {
  def apply[T <: Txn[T]](group: Timeline[T])
                        (implicit tx: T, universe: Universe[T]): TimelineFrame[T] =
    Impl(group)
}
trait TimelineFrame[T <: Txn[T]] extends lucre.swing.Window[T] {
  def view: TimelineView[T]
}