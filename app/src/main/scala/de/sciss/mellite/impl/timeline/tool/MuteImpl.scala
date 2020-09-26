/*
 *  MuteImpl.scala
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

import java.awt.event.MouseEvent
import java.awt.{Cursor, Point, Toolkit}

import de.sciss.lucre.expr.{BooleanObj, SpanLikeObj}
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{BasicTool, GUI, ObjTimelineView, TimelineTrackCanvas}
import de.sciss.mellite.TimelineTool.Mute
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.impl.tool.RubberBandTool
import de.sciss.mellite.Shapes
import de.sciss.synth.proc.{ObjKeys, Timeline}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object MuteImpl {
  private lazy val cursor: Cursor = {
    val tk  = Toolkit.getDefaultToolkit
    // val img = tk.createImage(Mellite.getClass.getResource("cursor-mute.png"))
    val img = GUI.getImage("cursor-mute.png")
    tk.createCustomCursor(img, new Point(4, 4), "Mute")
  }
}
final class MuteImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends CollectionImpl[T, Mute]
    with RubberBandTool[T, Mute, Int, ObjTimelineView[T]] {

  def defaultCursor: Cursor = MuteImpl.cursor
  val name                  = "Mute"
  val icon: Icon            = GUI.iconNormal(Shapes.Mute) // ToolsImpl.getIcon("mute")

  protected def commitObj(mute: Mute)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    // val imp = ExprImplicits[T]
    val newMute: BooleanObj[T] = obj.attr.$[BooleanObj](ObjKeys.attrMute) match {
      // XXX TODO: BooleanObj should have `not` operator
      case Some(BooleanObj.Var(vr)) => val vOld = vr().value; !vOld
      case other => !other.exists(_.value)
    }
    import de.sciss.equal.Implicits._
    val newMuteOpt = if (newMute === BooleanObj.newConst[T](false)) None else Some(newMute)
    val edit = EditAttrMap.expr[T, Boolean, BooleanObj](s"Adjust $name", obj, ObjKeys.attrMute, newMuteOpt)
    Some(edit)
  }

  protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: ObjTimelineView[T]): Unit = region match {
    case hm: ObjTimelineView.HasMute => dispatch(BasicTool.Adjust(Mute(!hm.muted)))
    case _ =>
  }
}
