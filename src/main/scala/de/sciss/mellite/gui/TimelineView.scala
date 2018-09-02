/*
 *  TimelineView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.timeline.{TimelineViewImpl => Impl}
import de.sciss.synth.proc.{Timeline, Workspace}
import de.sciss.synth.proc.gui.TransportView

import scala.swing.Action

object TimelineView {
  def apply[S <: Sys[S]](group: Timeline[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         undoManager: UndoManager): TimelineView[S] =
    Impl[S](group)

  /** Number of pixels for one unit of track height (convention). */
  final val TrackScale  = 8
  /** Minimum duration in sample-frames for some cases where it should be greater than zero. */
  final val MinDur      = 32

  final val DefaultTrackHeight = 8
}
trait TimelineView[S <: stm.Sys[S]] extends ViewHasWorkspace[S] with View.Editable[S] with CanBounce {
  def timelineModel   : TimelineModel
  def selectionModel  : TimelineObjView.SelectionModel[S]

  def timelineH: stm.Source[S#Tx , Timeline[S]]
  def timeline(implicit tx: S#Tx): Timeline[S]

  def canvas        : TimelineTrackCanvas[S]

  def globalView    : GlobalProcsView[S]
  def transportView : TransportView  [S]

  // ---- GUI actions ----
  def actionSelectAll           : Action
  def actionSelectFollowing     : Action
  def actionDelete              : Action
  def actionSplitObjects        : Action
  def actionCleanUpObjects      : Action
  def actionStopAllSound        : Action
  def actionClearSpan           : Action
  def actionRemoveSpan          : Action
  def actionAlignObjectsToCursor: Action
  def actionDropMarker          : Action
  def actionDropNamedMarker     : Action
}