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

package de.sciss.mellite.impl.grapheme.tool

import java.awt
import java.awt.event.MouseEvent

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.icons.raphael
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, LongObj, Obj}
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.impl.tool.RubberBandTool
import de.sciss.mellite.{GUI, GraphemeCanvas, GraphemeTool, ObjGraphemeView}
import de.sciss.proc.Grapheme
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class MoveImpl[T <: Txn[T]](protected val canvas: GraphemeCanvas[T])
  extends BasicGraphemeTool[T, GraphemeTool.Move]
    with RubberBandTool[T, GraphemeTool.Move, Double, ObjGraphemeView[T]]{

  import GraphemeTool.Move

  def getCursor: awt.Cursor = awt.Cursor.getPredefinedCursor(awt.Cursor.HAND_CURSOR)
  val name                  = "Move"
  val icon: Icon            = GUI.iconNormal(raphael.Shapes.Hand) // ToolsImpl.getIcon("openhand")

  override protected val hover: Boolean = true

  override protected def getCursor(e: MouseEvent, modelY: Double, pos: Long,
                                   childOpt: Option[C]): awt.Cursor =
    awt.Cursor.getPredefinedCursor(awt.Cursor.TEXT_CURSOR)

  protected def dragToParam(d: Drag): Move = {
    val eNow      = d.currentEvent
    val dTim0     = d.currentPos   - d.firstPos
    val dModelY0  = d.currentModelY - d.firstModelY
    val (dTim, dModelY) = if (eNow.isShiftDown) { // constrain movement to either horizontal or vertical
      val eBefore = d.firstEvent
      if (math.abs(eNow.getX - eBefore.getX) > math.abs(eNow.getY - eBefore.getY)) {  // horizontal
        (dTim0, 0d)
      } else {  // vertical
        (0L, dModelY0)
      }
    } else {  // unconstrained
      (dTim0, dModelY0)
    }

    Move(deltaTime = dTim, deltaModelY = dModelY, copy = d.currentEvent.isAltDown)
  }

  override protected def handleOutside(e: MouseEvent, pos: Long, modelY: Double): Unit =
    mkRubber(e, modelY = modelY, pos = pos)

  protected def commitObj(drag: Move)(time: LongObj[T], child: Obj[T], parent: Grapheme[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val minStart = TimelineNavigation.minStart(canvas.timelineModel)
    Edits.graphemeMoveOrCopy(time, child, parent, drag, minStart = minStart)
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
    //    val tl = graphemeModel.grapheme
    //    timeTrans.setLengthAndRate(tl.span.length, tl.rate)
    //    if (showDialog(box)) {
    //      val delta = timeTrans.translate(ggTime.value, ParamSpace.spcTimeSmpsD).value.toLong
    //      Some(Move(delta, 0, copy = false))
    //    } else
    None
  }
}
