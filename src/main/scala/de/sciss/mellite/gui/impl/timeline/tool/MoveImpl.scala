/*
 *  MoveImpl.scala
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

package de.sciss.mellite.gui.impl.timeline.tool

import java.awt.Cursor
import java.awt.event.MouseEvent

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.Edits
import de.sciss.mellite.gui.impl.tool.RubberBandTool
import de.sciss.mellite.gui.{GUI, ObjTimelineView, TimelineTool, TimelineTrackCanvas}
import de.sciss.synth.proc.Timeline
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class MoveImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S])
  extends BasicTimelineTool[S, TimelineTool.Move]
    with RubberBandTool[S, TimelineTool.Move, Int, ObjTimelineView[S]] {

  import TimelineTool.Move

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  val name                  = "Move"
  val icon: Icon            = GUI.iconNormal(raphael.Shapes.Hand) // ToolsImpl.getIcon("openhand")

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

  override protected def handleOutside(e: MouseEvent, hitTrack: Int, pos: Long): Unit =
    mkRubber(e, modelY = hitTrack, pos = pos)

  protected def commitObj(drag: Move)(span: SpanLikeObj[S], obj: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
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
