/*
 *  TimelineView.scala
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
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.proc.gui.TransportView
import de.sciss.proc.{Timeline, Universe}

import scala.swing.Action

object TimelineView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: Txn[T]](tl: Timeline[T])
                          (implicit tx: T, universe: Universe[T],
                           undoManager: UndoManager): TimelineView[T]
  }

  def apply[T <: Txn[T]](tl: Timeline[T])
                        (implicit tx: T, universe: Universe[T],
                         undoManager: UndoManager): TimelineView[T] =
    companion(tl)

  /** Number of pixels for one unit of track height (convention). */
  final val TrackScale  = 8
  /** Minimum duration in sample-frames for some cases where it should be greater than zero. */
  final val MinDur      = 32

  final val DefaultTrackHeight = 8

  final case class Drag[T <: LTxn[T]](universe: Universe[T], view: TimelineView[T])

  val Flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor
}
trait TimelineView[T <: LTxn[T]] /*extends TimelineObjView[T]*/ extends ObjView[T]
  with TimelineViewBase[T, Int, ObjTimelineView[T]] with CanBounce {

  type Repr = Timeline[T]

  override def canvas: TimelineTrackCanvas[T]

  def globalView    : GlobalProcsView[T]
  def transportView : TransportView  [T]

  // ---- further GUI actions ----
  def actionSplitObjects        : Action
  def actionCleanUpObjects      : Action
  def actionStopAllSound        : Action
  def actionClearSpan           : Action
  def actionRemoveSpan          : Action
  def actionAlignObjectsToCursor: Action
  def actionDropMarker          : Action
  def actionDropNamedMarker     : Action
}