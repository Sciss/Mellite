/*
 *  EditTimelineInsertRemoveObj.scala
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

package de.sciss.mellite.edit

import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.synth.proc.Timeline
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

// direction: true = insert, false = remove
private[edit] class EditTimelineInsertRemoveObj[T <: Txn[T]](direction: Boolean,
                                                           timelineH: Source[T, Timeline.Modifiable[T]],
                                                           spanH: Source[T, SpanLikeObj[T]],
                                                           elemH: Source[T, Obj[T]])(implicit cursor: Cursor[T])
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

  private def insert()(implicit tx: T): Unit = timelineH().add   (spanH(), elemH())
  private def remove()(implicit tx: T): Unit = timelineH().remove(spanH(), elemH())

  def perform()(implicit tx: T): Unit = if (direction) insert() else remove()
}

object EditTimelineInsertObj {
  def apply[T <: Txn[T]](name: String, timeline: Timeline.Modifiable[T], span: SpanLikeObj[T], elem: Obj[T])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val spanH     = tx.newHandle(span)
    val timelineH = tx.newHandle(timeline)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, timelineH, spanH, elemH)
    res.perform()
    res
  }

  private class Impl[T <: Txn[T]](name: String,
                                  timelineH: Source[T, Timeline.Modifiable[T]],
                                  spanH: Source[T, SpanLikeObj[T]],
                                  elemH: Source[T, Obj[T]])(implicit cursor: Cursor[T])
    extends EditTimelineInsertRemoveObj[T](true, timelineH, spanH, elemH) {

    override def getPresentationName: String = name
  }
}

object EditTimelineRemoveObj {
  def apply[T <: Txn[T]](name: String, timeline: Timeline.Modifiable[T], span: SpanLikeObj[T], elem: Obj[T])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val spanH     = tx.newHandle(span)
    val timelineH = tx.newHandle(timeline)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, timelineH, spanH, elemH)
    res.perform()
    res
  }

  private class Impl[T <: Txn[T]](name: String, timelineH: Source[T, Timeline.Modifiable[T]],
                                  spanH: Source[T, SpanLikeObj[T]],
                                  elemH: Source[T, Obj[T]])(implicit cursor: Cursor[T])
    extends EditTimelineInsertRemoveObj[T](false, timelineH, spanH, elemH) {

    override def getPresentationName: String = name
  }
}