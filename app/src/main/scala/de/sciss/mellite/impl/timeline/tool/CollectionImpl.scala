/*
 *  CollectionImpl.scala
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

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ObjTimelineView, TimelineTool, TimelineTrackCanvas}
import de.sciss.mellite.impl.tool.BasicCollectionTool
import de.sciss.synth.proc.Timeline
import javax.swing.undo.UndoableEdit

/** A more complete implementation for timeline tools that process selected regions.
  * It implements `commit` by aggregating individual region based
  * commits performed in the abstract method `commitObj`.
  */
trait CollectionImpl[T <: Txn[T], A] extends BasicCollectionTool[T, A, Int, ObjTimelineView[T]]
  with TimelineTool[T, A]{

  override protected def canvas: TimelineTrackCanvas[T]

  def commit(drag: A)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    lazy val tl = canvas.timeline
    val edits = canvas.selectionModel.iterator.flatMap { pv =>
      val span = pv.span
      val proc = pv.obj
      commitObj(drag)(span, proc, tl)
    } .toList
    val name = edits.headOption.fold("Edit") { ed =>
      val n = ed.getPresentationName
      val i = n.indexOf(' ')
      if (i < 0) n else n.substring(0, i)
    }
    CompoundEdit(edits, name)
  }

  protected def commitObj(drag: A)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit]
}
