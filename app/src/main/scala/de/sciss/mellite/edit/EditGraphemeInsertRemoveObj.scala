/*
 *  EditGraphemeInsertRemoveObj.scala
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

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.synth.proc.Grapheme
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

// direction: true = insert, false = remove
private[edit] class EditGraphemeInsertRemoveObj[T <: Txn[T]](direction: Boolean,
                                                             graphemeH: Source[T, Grapheme.Modifiable[T]],
                                                             timeH: Source[T, LongObj[T]],
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

  private def insert()(implicit tx: T): Unit = graphemeH().add   (timeH(), elemH())
  private def remove()(implicit tx: T): Unit = graphemeH().remove(timeH(), elemH())

  def perform()(implicit tx: T): Unit = if (direction) insert() else remove()
}

object EditGraphemeInsertObj {
  def apply[T <: Txn[T]](name: String, grapheme: Grapheme.Modifiable[T], time: LongObj[T], elem: Obj[T])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val timeH     = tx.newHandle(time)
    val graphemeH = tx.newHandle(grapheme)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, graphemeH, timeH, elemH)
    res.perform()
    res
  }

  private class Impl[T <: Txn[T]](name: String,
                                  graphemeH: Source[T, Grapheme.Modifiable[T]],
                                  timeH: Source[T, LongObj[T]],
                                  elemH: Source[T, Obj[T]])(implicit cursor: Cursor[T])
    extends EditGraphemeInsertRemoveObj[T](true, graphemeH, timeH, elemH) {

    override def getPresentationName: String = name
  }
}

object EditGraphemeRemoveObj {
  def apply[T <: Txn[T]](name: String, grapheme: Grapheme.Modifiable[T], time: LongObj[T], elem: Obj[T])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val timeH     = tx.newHandle(time)
    val graphemeH = tx.newHandle(grapheme)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, graphemeH, timeH, elemH)
    res.perform()
    res
  }

  private class Impl[T <: Txn[T]](name: String, graphemeH: Source[T, Grapheme.Modifiable[T]],
                                  timeH: Source[T, LongObj[T]],
                                  elemH: Source[T, Obj[T]])(implicit cursor: Cursor[T])
    extends EditGraphemeInsertRemoveObj[T](false, graphemeH, timeH, elemH) {

    override def getPresentationName: String = name
  }
}