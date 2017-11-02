/*
 *  FunctionImpl.scala
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
package impl
package tracktool

import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.icons.raphael
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditTimelineInsertObj
import de.sciss.span.Span
import de.sciss.synth.proc.Proc

final class FunctionImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S], tlv: TimelineView[S])
  extends RegionLike[S, TrackTool.Function] with Dragging[S, TrackTool.Function] {

  import TrackTool.Function

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
  val name                  = "Function"
  val icon: Icon            = GUI.iconNormal(raphael.Shapes.Cogs)

  protected type Initial = Unit

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[TimelineObjView[S]]): Unit = {
    handleMouseSelection(e, regionOpt)
    regionOpt match {
      case Some(region) =>
        if (e.getClickCount == 2 && region.isViewable) {
          import tlv.{cursor, workspace}
          cursor.step { implicit tx =>
            region.openView(None)  /// XXX TODO - find window
          }
        }

      case _  => new Drag(e, hitTrack, pos, ())
    }
  }

  protected def dragToParam(d: Drag): Function = {
    val dStart  = math.min(d.firstPos, d.currentPos)
    val dStop   = math.max(dStart + BasicRegion.MinDur, math.max(d.firstPos, d.currentPos))
    val dTrkIdx = math.min(d.firstTrack, d.currentTrack)
    val dTrkH   = math.max(d.firstTrack, d.currentTrack) - dTrkIdx + 1

    Function(trackIndex = dTrkIdx, trackHeight = dTrkH, span = Span(dStart, dStop))
  }

  def commit(drag: Function)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] =
    canvas.timeline.modifiableOption.map { g =>
      val span  = SpanLikeObj.newVar[S](SpanLikeObj.newConst(drag.span)) // : SpanLikeObj[S]
      val p     = Proc[S]
      val obj   = p // Obj(Proc.Elem(p))
      obj.attr.put(TimelineObjView.attrTrackIndex , IntObj.newVar(IntObj.newConst(drag.trackIndex )))
      obj.attr.put(TimelineObjView.attrTrackHeight, IntObj.newVar(IntObj.newConst(drag.trackHeight)))
      log(s"Add function region $p, span = ${drag.span}, trackIndex = ${drag.trackIndex}")
      // import SpanLikeObj.serializer
      EditTimelineInsertObj(s"Insert $name", g, span, obj)
      // g.add(span, obj)

      // canvas.selectionModel.clear()
      // canvas.selectionModel += ?
    }
}
