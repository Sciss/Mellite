/*
 *  AddImpl.scala
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

package de.sciss.mellite.impl.timeline.tool

import java.awt.Cursor
import java.awt.event.MouseEvent

import de.sciss.icons.raphael
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.Mellite.log
import de.sciss.mellite.edit.EditTimelineInsertObj
import de.sciss.mellite.impl.tool.{CollectionToolLike, DraggingTool}
import de.sciss.mellite.{BasicTools, GUI, ObjTimelineView, Shapes, TimelineTool, TimelineTrackCanvas, TimelineView}
import de.sciss.span.Span
import de.sciss.synth.proc.Proc
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class AddImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T], tlv: TimelineView[T])
  extends CollectionToolLike[T, TimelineTool.Add, Int, ObjTimelineView[T]]
    with DraggingTool[T, TimelineTool.Add, Int]
    with TimelineTool[T, TimelineTool.Add] {

  import TimelineTool.Add

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
  val name                  = "Add Process" // "Function"
//  val icon: Icon            = GUI.iconNormal(raphael.Shapes.Cogs)
  val icon: Icon            = GUI.iconNormal(Shapes.plus(raphael.Shapes.Cogs))

  protected type Initial = Unit

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[ObjTimelineView[T]]): Unit = {
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

  protected def dragToParam(d: Drag): Add = {
    val dStart  = math.min(d.firstPos, d.currentPos)
    val dStop   = math.max(dStart + BasicTools.MinDur, math.max(d.firstPos, d.currentPos))
    val dTrkIdx = math.min(d.firstModelY, d.currentModelY)
    val dTrkH   = math.max(d.firstModelY, d.currentModelY) - dTrkIdx + 1

    Add(modelYOffset = dTrkIdx, modelYExtent = dTrkH, span = Span(dStart, dStop))
  }

  def commit(drag: Add)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    canvas.timeline.modifiableOption.map { g =>
      val span  = SpanLikeObj.newVar[T](SpanLikeObj.newConst(drag.span)) // : SpanLikeObj[T]
      val p     = Proc[T]()
      val obj   = p // Obj(Proc.Elem(p))
      obj.attr.put(ObjTimelineView.attrTrackIndex , IntObj.newVar(IntObj.newConst(drag.modelYOffset)))
      obj.attr.put(ObjTimelineView.attrTrackHeight, IntObj.newVar(IntObj.newConst(drag.modelYExtent)))
      log(s"Add function region $p, span = ${drag.span}, trackIndex = ${drag.modelYOffset}")
      // import SpanLikeObj.serializer
      EditTimelineInsertObj(s"Insert $name", g, span, obj)
      // g.add(span, obj)

      // canvas.selectionModel.clear()
      // canvas.selectionModel += ?
    }
}
