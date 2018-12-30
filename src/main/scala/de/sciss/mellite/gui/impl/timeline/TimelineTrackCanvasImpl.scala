/*
 *  TimelineProcCanvasImpl.scala
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
package impl
package timeline

import de.sciss.audiowidgets.impl.TimelineCanvasImpl
import de.sciss.lucre.synth.Sys
import TimelineTool.EmptyRubber
import de.sciss.mellite.gui.BasicTool.{DragAdjust, DragCancel, DragEnd, DragRubber}

trait TimelineTrackCanvasImpl[S <: Sys[S]] extends TimelineCanvasImpl with TimelineTrackCanvas[S] {
  final val timelineTools: TimelineTools[S] = TimelineTools(this)

  import TimelineTools._

  protected var toolState: Option[Any]
  protected var rubberState: DragRubber[Int] = EmptyRubber

  private[this] var _trackIndexOffset = 0

  def trackIndexOffset: Int = _trackIndexOffset
  def trackIndexOffset_=(value: Int): Unit = if (_trackIndexOffset != value) {
    _trackIndexOffset = value
    repaint()
  }

  final def screenToModelY(y    : Int): Int = y / TimelineView.TrackScale + trackIndexOffset
  final def modelYToScreen(track: Int): Int = (track - trackIndexOffset) * TimelineView.TrackScale

  final def modelYBox(a: Int, b: Int): (Int, Int) = if (a < b) (a, b - a + 1) else (b, a - b + 1)

  private[this] val toolListener: TimelineTool.Listener = {
    // case TrackTool.DragBegin =>
    case DragCancel =>
      log(s"Drag cancel $toolState")
      if (toolState.isDefined) {
        toolState   = None
        repaint()
      } else if (rubberState.isValid) {
        rubberState = EmptyRubber
        repaint()
      }

    case DragEnd =>
      log(s"Drag end $toolState")
      toolState.fold[Unit] {
        if (rubberState.isValid) {
          rubberState = EmptyRubber
          repaint()
        }
      } { state =>
        toolState   = None
        rubberState = EmptyRubber
        commitToolChanges(state)
        repaint()
      }

    case DragAdjust(value) =>
      // log(s"Drag adjust $value")
      val some = Some(value)
      if (toolState != some) {
        toolState = some
        repaint()
      }

    case BasicTool.Adjust(state) =>
      log(s"Tool commit $state")
      toolState = None
      commitToolChanges(state)
      repaint()

    case state: DragRubber[Int] =>    // XXX TODO: erased type arg, not pretty
      log(s"Tool rubber $state")
      rubberState = state
      repaint()
  }

  timelineTools.addListener {
    case ToolChanged(change) =>
      change.before.removeListener(toolListener)
      change.now   .addListener   (toolListener)
    case VisualBoostChanged   (_) => repaint()
    case FadeViewModeChanged  (_) => repaint()
    case RegionViewModeChanged(_) => repaint()
  }
  timelineTools.currentTool.addListener(toolListener)

  private[this] val selectionListener: SelectionModel.Listener[S, TimelineObjView[S]] = {
    case SelectionModel.Update(_ /* added */, _ /* removed */) =>
      canvasComponent.repaint() // XXX TODO: dirty rectangle optimization
  }

  override protected def componentShown(): Unit = {
    super.componentShown()
    selectionModel.addListener(selectionListener)
  }

  override protected def componentHidden(): Unit = {
    super.componentHidden()
    selectionModel.removeListener(selectionListener)
  }

  protected def commitToolChanges(value: Any): Unit
}