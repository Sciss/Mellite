/*
 *  EditAddRemoveFScapeOutput.scala
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

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.{Cursor, Obj, Source, Txn => LTxn}
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

// direction: true = insert, false = remove
// XXX TODO - should disconnect links and restore them in undo
private[edit] final class EditAddRemoveFScapeOutput[T <: LTxn[T]](isAdd: Boolean,
                                                                  fscapeH: Source[T, FScape[T]],
                                                                  key: String, tpe: Obj.Type)(implicit cursor: Cursor[T])
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
    val fscape = fscapeH()
    val outputs = fscape.outputs
    if (isAdd ^ isUndo)
      outputs.add   (key, tpe)
    else
      outputs.remove(key)
  }

  override def getPresentationName = s"${if (isAdd) "Add" else "Remove"} Output"
}
object EditAddFScapeOutput {
  def apply[T <: LTxn[T]](fscape: FScape[T], key: String, tpe: Obj.Type)
                         (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val fscapeH = tx.newHandle(fscape)
    val res = new EditAddRemoveFScapeOutput(isAdd = true, fscapeH = fscapeH, key = key, tpe = tpe)
    res.perform()
    res
  }
}

object EditRemoveFScapeOutput {
  def apply[T <: LTxn[T]](output: FScape.Output[T])
                         (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val fscape  = output.fscape
    val key     = output.key
    val tpe     = output.tpe
    val fscapeH = tx.newHandle(fscape)
    val res = new EditAddRemoveFScapeOutput(isAdd = false, fscapeH = fscapeH, key = key, tpe = tpe)
    res.perform()
    res
  }
}