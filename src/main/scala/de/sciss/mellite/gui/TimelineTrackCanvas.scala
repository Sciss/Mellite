/*
 *  TimelineTrackCanvas.scala
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

import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Timeline

trait TimelineTrackCanvas[S <: Sys[S]] extends TimelineCanvas2D[S, Int, TimelineObjView[S]] {
  def timeline(implicit tx: S#Tx): Timeline[S]

  def timelineTools: TimelineTools[S]
}