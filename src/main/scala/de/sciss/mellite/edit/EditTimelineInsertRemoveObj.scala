/*
 *  EditTimelineInsertRemoveObj.scala
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

package de.sciss.mellite.edit

import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.synth.proc.Timeline
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

// direction: true = insert, false = remove
private[edit] class EditTimelineInsertRemoveObj[S <: Sys[S]](direction: Boolean,
                                                           timelineH: stm.Source[S#Tx, Timeline.Modifiable[S]],
                                                           spanH: stm.Source[S#Tx, SpanLikeObj[S]],
                                                           elemH: stm.Source[S#Tx, Obj[S]])(implicit cursor: stm.Cursor[S])
  extends AbstractUndoableEdit {

  override def undo(): Unit = {
    super.undo()
    cursor.step { implicit tx =>
      if (direction) remove() else insert()
    }
  }

  override def redo(): Unit = {
    super.redo()
    cursor.step { implicit tx => perform() }
  }

  override def die(): Unit = {
    val hasBeenDone = canUndo
    super.die()
    if (!hasBeenDone) {
      // XXX TODO: dispose()
    }
  }

  private def insert()(implicit tx: S#Tx): Unit = timelineH().add   (spanH(), elemH())
  private def remove()(implicit tx: S#Tx): Unit = timelineH().remove(spanH(), elemH())

  def perform()(implicit tx: S#Tx): Unit = if (direction) insert() else remove()
}

object EditTimelineInsertObj {
  def apply[S <: Sys[S]](name: String, timeline: Timeline.Modifiable[S], span: SpanLikeObj[S], elem: Obj[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val spanH     = tx.newHandle(span)
    val timelineH = tx.newHandle(timeline)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, timelineH, spanH, elemH)
    res.perform()
    res
  }

  private class Impl[S <: Sys[S]](name: String,
                                  timelineH: stm.Source[S#Tx, Timeline.Modifiable[S]],
                                  spanH: stm.Source[S#Tx, SpanLikeObj[S]],
                                  elemH: stm.Source[S#Tx, Obj[S]])(implicit cursor: stm.Cursor[S])
    extends EditTimelineInsertRemoveObj[S](true, timelineH, spanH, elemH) {

    override def getPresentationName: String = name
  }
}

object EditTimelineRemoveObj {
  def apply[S <: Sys[S]](name: String, timeline: Timeline.Modifiable[S], span: SpanLikeObj[S], elem: Obj[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val spanH     = tx.newHandle(span)
    val timelineH = tx.newHandle(timeline)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, timelineH, spanH, elemH)
    res.perform()
    res
  }

  private class Impl[S <: Sys[S]](name: String, timelineH: stm.Source[S#Tx, Timeline.Modifiable[S]],
                                  spanH: stm.Source[S#Tx, SpanLikeObj[S]],
                                  elemH: stm.Source[S#Tx, Obj[S]])(implicit cursor: stm.Cursor[S])
    extends EditTimelineInsertRemoveObj[S](false, timelineH, spanH, elemH) {

    override def getPresentationName: String = name
  }
}