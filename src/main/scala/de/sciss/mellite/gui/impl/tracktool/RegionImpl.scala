/*
 *  RegionImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
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

import java.awt.event.MouseEvent
import javax.swing.undo.UndoableEdit

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.Timeline

/** A more complete implementation for track tools that process selected regions.
  * It implements `handlePress` to update the region selection and then
  * for the currently hit region invoke the `handleSelect` method.
  * It also implements `commit` by aggregating individual region based
  * commits performed in the abstract method `commitObj`.
  */
trait RegionImpl[S <: Sys[S], A] extends RegionLike[S, A] {
  tool =>

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[TimelineObjView[S]]): Unit = {
    handleMouseSelection(e, regionOpt)
    // now go on if region is selected
    regionOpt.fold[Unit] {
      handleOutside(e, hitTrack, pos)
    } { region =>
      if (canvas.selectionModel.contains(region)) handleSelect(e, hitTrack, pos, region)
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

  protected def commitObj(drag: A)(span: SpanLikeObj[S], proc: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]

  protected def handleSelect (e: MouseEvent, hitTrack: Int, pos: Long, region: TimelineObjView[S]): Unit

  protected def handleOutside(e: MouseEvent, hitTrack: Int, pos: Long): Unit = ()
}