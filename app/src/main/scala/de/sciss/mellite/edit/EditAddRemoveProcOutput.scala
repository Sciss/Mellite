/*
 *  EditAddRemoveOutput.scala
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

import de.sciss.lucre.{Cursor, Source, Txn}
import de.sciss.proc.Proc
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

// direction: true = insert, false = remove
// XXX TODO - should disconnect links and restore them in undo
private[edit] final class EditAddRemoveProcOutput[T <: Txn[T]](isAdd: Boolean,
                                                               procH: Source[T, Proc[T]],
                                                               key: String)(implicit cursor: Cursor[T])
  extends AbstractUndoableEdit {

  override def undo(): Unit = {
    super.undo()
    cursor.step { implicit tx => perform(isUndo = true) }
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

  def perform()(implicit tx: T): Unit = perform(isUndo = false)

  private def perform(isUndo: Boolean)(implicit tx: T): Unit = {
    val proc    = procH()
    val outputs = proc.outputs
    if (isAdd ^ isUndo)
      outputs.add   (key)
    else
      outputs.remove(key)
  }

  override def getPresentationName = s"${if (isAdd) "Add" else "Remove"} Output"
}
object EditAddProcOutput {
  def apply[T <: Txn[T]](proc: Proc[T], key: String)
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val procH = tx.newHandle(proc)
    val res = new EditAddRemoveProcOutput(isAdd = true, procH = procH, key = key)
    res.perform()
    res
  }
}

object EditRemoveProcOutput {
  def apply[T <: Txn[T]](proc: Proc[T], key: String)
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val procH = tx.newHandle(proc)
    val res = new EditAddRemoveProcOutput(isAdd = false, procH = procH, key = key)
    res.perform()
    res
  }
}