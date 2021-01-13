/*
 *  MoveImpl.scala
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

package de.sciss.mellite.impl.timeline.tool

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.icons.raphael
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj}
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.impl.tool.RubberBandTool
import de.sciss.mellite.{GUI, ObjTimelineView, TimelineTool, TimelineTrackCanvas}
import de.sciss.proc.Timeline

import java.awt
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class MoveImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Move]
    with RubberBandTool[T, TimelineTool.Move, Int, ObjTimelineView[T]] {

  import TimelineTool.Move

  val name                  = "Move"
  val icon: Icon            = GUI.iconNormal(raphael.Shapes.Hand) // ToolsImpl.getIcon("openhand")

  override protected val hover: Boolean = true

  override protected def getCursor(e: MouseEvent, modelY: Int, pos: Long, childOpt: Option[C]): awt.Cursor =
    if (childOpt.isEmpty) defaultCursor else awt.Cursor.getPredefinedCursor(awt.Cursor.HAND_CURSOR)

  protected def dragToParam(d: Drag): Move = {
    val eNow  = d.currentEvent
    val dTim0 = d.currentPos   - d.firstPos
    val dTrk0 = d.currentModelY - d.firstModelY
    val (dTim, dTrk) = if (eNow.isShiftDown) { // constrain movement to either horizontal or vertical
      val eBefore = d.firstEvent
      if (math.abs(eNow.getX - eBefore.getX) > math.abs(eNow.getY - eBefore.getY)) {  // horizontal
        (dTim0, 0)
      } else {  // vertical
        (0L, dTrk0)
      }
    } else {  // unconstrained
      (dTim0, dTrk0)
    }

    Move(deltaTime = dTim, deltaTrack = dTrk, copy = d.currentEvent.isAltDown)
  }

  override protected def handleOutside(e: MouseEvent, pos: Long, modelY: Int): Unit =
    mkRubber(e, modelY = modelY, pos = pos)

  protected def commitObj(drag: Move)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val minStart = TimelineNavigation.minStart(canvas.timelineModel)
    Edits.timelineMoveOrCopy(span, obj, timeline, drag, minStart = minStart)
  }

  protected def dialog(): Option[Move] = {
    println("Not yet implemented - movement dialog")
    //    val box             = Box.createHorizontalBox
    //    val timeTrans       = new DefaultUnitTranslator()
    //    val ggTime          = new BasicParamField(timeTrans)
    //    val spcTimeHHMMSSD  = new ParamSpace(Double.NegativeInfinity, Double.PositiveInfinity, 0.0, 1, 3, 0.0,
    //      ParamSpace.TIME | ParamSpace.SECS | ParamSpace.HHMMSS | ParamSpace.OFF)
    //    ggTime.addSpace(spcTimeHHMMSSD)
    //    ggTime.addSpace(ParamSpace.spcTimeSmpsD)
    //    ggTime.addSpace(ParamSpace.spcTimeMillisD)
    //    GUI.setInitialDialogFocus(ggTime)
    //    box.add(new JLabel("Move by:"))
    //    box.add(Box.createHorizontalStrut(8))
    //    box.add(ggTime)
    //
    //    val tl = timelineModel.timeline
    //    timeTrans.setLengthAndRate(tl.span.length, tl.rate)
    //    if (showDialog(box)) {
    //      val delta = timeTrans.translate(ggTime.value, ParamSpace.spcTimeSmpsD).value.toLong
    //      Some(Move(delta, 0, copy = false))
    //    } else
    None
  }
}
