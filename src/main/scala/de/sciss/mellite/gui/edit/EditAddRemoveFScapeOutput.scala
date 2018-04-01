/*
 *  EditAddRemoveOutput.scala
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

import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}

// direction: true = insert, false = remove
// XXX TODO - should disconnect links and restore them in undo
private[edit] final class EditAddRemoveFScapeOutput[S <: Sys[S]](isAdd: Boolean,
                                                               fscapeH: stm.Source[S#Tx, FScape[S]],
                                                               key: String, tpe: Obj.Type)(implicit cursor: stm.Cursor[S])
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

  def perform()(implicit tx: S#Tx): Unit = perform(isUndo = false)

  private def perform(isUndo: Boolean)(implicit tx: S#Tx): Unit = {
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
  def apply[S <: Sys[S]](fscape: FScape[S], key: String, tpe: Obj.Type)
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val fscapeH = tx.newHandle(fscape)
    val res = new EditAddRemoveFScapeOutput(isAdd = true, fscapeH = fscapeH, key = key, tpe = tpe)
    res.perform()
    res
  }
}

object EditRemoveFScapeOutput {
  def apply[S <: Sys[S]](output: FScape.Output[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val fscape  = output.fscape
    val key     = output.key
    val tpe     = output.tpe
    val fscapeH = tx.newHandle(fscape)
    val res = new EditAddRemoveFScapeOutput(isAdd = false, fscapeH = fscapeH, key = key, tpe = tpe)
    res.perform()
    res
  }
}