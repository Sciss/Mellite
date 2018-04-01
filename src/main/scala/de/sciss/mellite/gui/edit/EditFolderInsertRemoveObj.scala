/*
 *  EditFolderInsertRemoveObj.scala
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

package de.sciss.mellite.gui.edit

import javax.swing.undo.{AbstractUndoableEdit, CannotRedoException, CannotUndoException, UndoableEdit}

import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Sys, Obj}
import de.sciss.synth.proc.Folder

// direction: true = insert, false = remove
private[edit] class EditFolderInsertRemoveObj[S <: Sys[S]](isInsert: Boolean, nodeType: String,
                         parentH: stm.Source[S#Tx, Folder[S]],
                         index: Int,
                         childH: stm.Source[S#Tx, Obj[S]])(implicit cursor: stm.Cursor[S])
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

  private def insert()(implicit tx: S#Tx): Boolean = {
    val parent = parentH()
    if (parent.size >= index) {
      val child = childH()
      parent.insert(index, child)
      true
    } else false
  }

  private def remove()(implicit tx: S#Tx): Boolean = {
    val parent = parentH()
    if (parent.size > index) {
      parent.removeAt(index)
      true
    } else false
  }

  def perform()(implicit tx: S#Tx): Unit = {
    val success = if (isInsert) insert() else remove()
    if (!success) throw new CannotRedoException()
  }

  override def getPresentationName = s"${if (isInsert) "Insert" else "Remove"} $nodeType"
}

object EditFolderInsertObj {
  def apply[S <: Sys[S]](nodeType: String, parent: Folder[S], index: Int, child: Obj[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val parentH = tx.newHandle(parent)
    val childH  = tx.newHandle(child)
    val res     = new EditFolderInsertRemoveObj(true, nodeType, parentH, index, childH)
    res.perform()
    res
  }
}

object EditFolderRemoveObj {
  def apply[S <: Sys[S]](nodeType: String, parent: Folder[S], index: Int, child: Obj[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val parentH = tx.newHandle(parent)
    val childH  = tx.newHandle(child)
    val res     = new EditFolderInsertRemoveObj(false, nodeType, parentH, index, childH)
    res.perform()
    res
  }
}