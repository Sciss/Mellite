/*
 *  MuteImpl.scala
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

import java.awt
import java.awt.event.MouseEvent
import java.awt.{Point, Toolkit}

import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, SpanLikeObj}
import de.sciss.mellite.TimelineTool.Mute
import de.sciss.mellite.edit.Edits
import de.sciss.mellite.impl.tool.RubberBandTool
import de.sciss.mellite.{BasicTool, GUI, ObjTimelineView, Shapes, TimelineTrackCanvas}
import de.sciss.proc.Timeline
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object MuteImpl {
  private lazy val cursor: awt.Cursor = {
    val tk  = Toolkit.getDefaultToolkit
    // val img = tk.createImage(Mellite.getClass.getResource("cursor-mute.png"))
    val img = GUI.getImage("cursor-mute.png")
    tk.createCustomCursor(img, new Point(4, 4), "Mute")
  }
}
final class MuteImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends CollectionImpl[T, Mute]
    with RubberBandTool[T, Mute, Int, ObjTimelineView[T]] {

  val name                  = "Mute"
  val icon: Icon            = GUI.iconNormal(Shapes.Mute) // ToolsImpl.getIcon("mute")

  override protected val hover: Boolean = true

  override protected def getCursor(e: MouseEvent, modelY: Int, pos: Long, childOpt: Option[C]): awt.Cursor =
    if (childOpt.isEmpty) defaultCursor else MuteImpl.cursor

  protected def commitObj(mute: Mute)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] =
    Edits.mute(obj, mute)

  protected def handleSelect(e: MouseEvent, pos: Long, modelY: Int, child: C): Unit = child match {
    case hm: ObjTimelineView.HasMute => dispatch(BasicTool.Adjust(Mute(!hm.muted)))
    case _ =>
  }
}
