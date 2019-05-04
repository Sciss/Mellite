/*
 *  MuteImpl.scala
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

import java.awt.event.MouseEvent
import java.awt.{Cursor, Point, Toolkit}

import de.sciss.lucre.expr.{BooleanObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.TimelineTool.Mute
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.mellite.gui.impl.tool.RubberBandTool
import de.sciss.mellite.gui.{BasicTool, GUI, Shapes, ObjTimelineView, TimelineTrackCanvas}
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
final class MuteImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S])
  extends CollectionImpl[S, Mute]
    with RubberBandTool[S, Mute, Int, ObjTimelineView[S]] {

  def defaultCursor: Cursor = MuteImpl.cursor
  val name                  = "Mute"
  val icon: Icon            = GUI.iconNormal(Shapes.Mute) // ToolsImpl.getIcon("mute")

  protected def commitObj(mute: Mute)(span: SpanLikeObj[S], obj: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    // val imp = ExprImplicits[S]
    val newMute: BooleanObj[S] = obj.attr.$[BooleanObj](ObjKeys.attrMute) match {
      // XXX TODO: BooleanObj should have `not` operator
      case Some(BooleanObj.Var(vr)) => val vOld = vr().value; !vOld
      case other => !other.exists(_.value)
    }
    import de.sciss.equal.Implicits._
    val newMuteOpt = if (newMute === BooleanObj.newConst[S](false)) None else Some(newMute)
    val edit = EditAttrMap.expr[S, Boolean, BooleanObj](s"Adjust $name", obj, ObjKeys.attrMute, newMuteOpt)
    Some(edit)
  }

  protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: ObjTimelineView[S]): Unit = region match {
    case hm: ObjTimelineView.HasMute => dispatch(BasicTool.Adjust(Mute(!hm.muted)))
    case _ =>
  }
}
