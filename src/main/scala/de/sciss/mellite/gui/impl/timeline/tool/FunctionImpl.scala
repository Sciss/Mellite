/*
 *  FunctionImpl.scala
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

package de.sciss.mellite.gui.impl.timeline.tool

import java.awt.Cursor
import java.awt.event.MouseEvent

import de.sciss.icons.raphael
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditTimelineInsertObj
import de.sciss.mellite.gui.impl.{CollectionToolLike, DraggingTool}
import de.sciss.mellite.gui.{BasicTools, GUI, TimelineObjView, TimelineTool, TimelineTrackCanvas, TimelineView}
import de.sciss.mellite.log
import de.sciss.span.Span
import de.sciss.synth.proc.Proc
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class FunctionImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S], tlv: TimelineView[S])
  extends CollectionToolLike[S, TimelineTool.Function, Int, TimelineObjView[S]]
    with DraggingTool[S, TimelineTool.Function, Int]
    with TimelineTool[S, TimelineTool.Function] {

  import TimelineTool.Function

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  val name                  = "Function"
  val icon: Icon            = GUI.iconNormal(raphael.Shapes.Cogs)

  protected type Initial = Unit

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[TimelineObjView[S]]): Unit = {
    handleMouseSelection(e, regionOpt)
    regionOpt match {
      case Some(region) =>
        if (e.getClickCount == 2 && region.isViewable) {
          import tlv.{cursor, universe}
          cursor.step { implicit tx =>
            region.openView(None)  /// XXX TODO - find window
          }
        }

      case _  => new Drag(e, hitTrack, pos, ())
    }
  }

  protected def dragToParam(d: Drag): Function = {
    val dStart  = math.min(d.firstPos, d.currentPos)
    val dStop   = math.max(dStart + BasicTools.MinDur, math.max(d.firstPos, d.currentPos))
    val dTrkIdx = math.min(d.firstModelY, d.currentModelY)
    val dTrkH   = math.max(d.firstModelY, d.currentModelY) - dTrkIdx + 1

    Function(modelYOffset = dTrkIdx, modelYExtent = dTrkH, span = Span(dStart, dStop))
  }

  def commit(drag: Function)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] =
    canvas.timeline.modifiableOption.map { g =>
      val span  = SpanLikeObj.newVar[S](SpanLikeObj.newConst(drag.span)) // : SpanLikeObj[S]
      val p     = Proc[S]
      val obj   = p // Obj(Proc.Elem(p))
      obj.attr.put(TimelineObjView.attrTrackIndex , IntObj.newVar(IntObj.newConst(drag.modelYOffset)))
      obj.attr.put(TimelineObjView.attrTrackHeight, IntObj.newVar(IntObj.newConst(drag.modelYExtent)))
      log(s"Add function region $p, span = ${drag.span}, trackIndex = ${drag.modelYOffset}")
      // import SpanLikeObj.serializer
      EditTimelineInsertObj(s"Insert $name", g, span, obj)
      // g.add(span, obj)

      // canvas.selectionModel.clear()
      // canvas.selectionModel += ?
    }
}
