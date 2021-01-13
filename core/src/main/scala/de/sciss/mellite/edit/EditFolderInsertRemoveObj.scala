/*
 *  EditFolderInsertRemoveObj.scala
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

package de.sciss.mellite.edit

import de.sciss.lucre.{Cursor, Folder, Obj, Source, Txn}
import javax.swing.undo.{AbstractUndoableEdit, CannotRedoException, CannotUndoException, UndoableEdit}

// direction: true = insert, false = remove
private[edit] class EditFolderInsertRemoveObj[T <: Txn[T]](isInsert: Boolean, nodeType: String,
                         parentH: Source[T, Folder[T]],
                         index: Int,
                         childH: Source[T, Obj[T]])(implicit cursor: Cursor[T])
  extends AbstractUndoableEdit {

  override def undo(): Unit = {
    super.undo()
    cursor.step { implicit tx =>
      val success = if (isInsert) remove() else insert()
      if (!success) throw new CannotUndoException()
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

  private def insert()(implicit tx: T): Boolean = {
    val parent = parentH()
    if (parent.size >= index) {
      val child = childH()
      parent.insert(index, child)
      true
    } else false
  }

  private def remove()(implicit tx: T): Boolean = {
    val parent = parentH()
    if (parent.size > index) {
      parent.removeAt(index)
      true
    } else false
  }

  def perform()(implicit tx: T): Unit = {
    val success = if (isInsert) insert() else remove()
    if (!success) throw new CannotRedoException()
  }

  override def getPresentationName = s"${if (isInsert) "Insert" else "Remove"} $nodeType"
}

object EditFolderInsertObj {
  def apply[T <: Txn[T]](nodeType: String, parent: Folder[T], index: Int, child: Obj[T])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val parentH = tx.newHandle(parent)
    val childH  = tx.newHandle(child)
    val res     = new EditFolderInsertRemoveObj(true, nodeType, parentH, index, childH)
    res.perform()
    res
  }
}

object EditFolderRemoveObj {
  def apply[T <: Txn[T]](nodeType: String, parent: Folder[T], index: Int, child: Obj[T])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val parentH = tx.newHandle(parent)
    val childH  = tx.newHandle(child)
    val res     = new EditFolderInsertRemoveObj(false, nodeType, parentH, index, childH)
    res.perform()
    res
  }
}