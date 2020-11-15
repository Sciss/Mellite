/*
 *  TimelineTrackCanvas.scala
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

import de.sciss.lucre.Txn
import de.sciss.proc.Timeline

trait TimelineTrackCanvas[T <: Txn[T]] extends TimelineCanvas2D[T, Int, ObjTimelineView[T]] {
  def timeline(implicit tx: T): Timeline[T]

  def timelineTools: TimelineTools[T]
}