/*
 *  TimelineProcCanvas.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.audiowidgets.TimelineCanvas
import de.sciss.lucre.stm.Sys
import de.sciss.span.Span
import de.sciss.synth.proc.Timeline

trait TimelineProcCanvas[S <: Sys[S]] extends TimelineCanvas {
  def timeline(implicit tx: S#Tx): Timeline[S]

  def selectionModel: TimelineObjView.SelectionModel[S]

  def intersect(span: Span): Iterator[TimelineObjView[S]]

  def findRegion(frame: Long, hitTrack: Int): Option[TimelineObjView[S]]

  def findRegions(r: TrackTool.Rectangular): Iterator[TimelineObjView[S]]

  def screenToTrack(y    : Int): Int
  def trackToScreen(track: Int): Int

  def trackTools: TrackTools[S]
}