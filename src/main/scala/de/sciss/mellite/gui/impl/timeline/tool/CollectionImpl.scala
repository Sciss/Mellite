/*
 *  CollectionImpl.scala
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

import java.awt.event.MouseEvent

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.CollectionToolLike
import de.sciss.mellite.gui.{TimelineObjView, TimelineTool, TimelineTrackCanvas}
import de.sciss.synth.proc.Timeline
import javax.swing.undo.UndoableEdit

/** A more complete implementation for timeline tools that process selected regions.
  * It implements `handlePress` to update the region selection and then
  * for the currently hit region invoke the `handleSelect` method.
  * It also implements `commit` by aggregating individual region based
  * commits performed in the abstract method `commitObj`.
  */
trait CollectionImpl[S <: Sys[S], A] extends CollectionToolLike[S, A, Int, TimelineObjView[S]]
  with TimelineTool[S, A]{

  tool =>

  override protected def canvas: TimelineTrackCanvas[S]

  protected def handlePress(e: MouseEvent, modelY: Int, pos: Long, childOpt: Option[TimelineObjView[S]]): Unit = {
    handleMouseSelection(e, childOpt)
    // now go on if region is selected
    childOpt.fold[Unit] {
      handleOutside(e, modelY, pos)
    } { region =>
      if (canvas.selectionModel.contains(region)) handleSelect(e, modelY, pos, region)
    }
  }

  def commit(drag: A)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
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

  protected def commitObj(drag: A)(span: SpanLikeObj[S], obj: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]

  protected def handleSelect (e: MouseEvent, modelY: Int, pos: Long, region: TimelineObjView[S]): Unit

  protected def handleOutside(e: MouseEvent, modelY: Int, pos: Long): Unit = ()
}
