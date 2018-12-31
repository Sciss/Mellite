/*
 *  EditGraphemeInsertRemoveObj.scala
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

package de.sciss.mellite
package gui
package edit

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}
import de.sciss.synth.proc.Grapheme
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

// direction: true = insert, false = remove
private[edit] class EditGraphemeInsertRemoveObj[S <: Sys[S]](direction: Boolean,
                                                             graphemeH: stm.Source[S#Tx, Grapheme.Modifiable[S]],
                                                             timeH: stm.Source[S#Tx, LongObj[S]],
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

  private def insert()(implicit tx: S#Tx): Unit = graphemeH().add   (timeH(), elemH())
  private def remove()(implicit tx: S#Tx): Unit = graphemeH().remove(timeH(), elemH())

  def perform()(implicit tx: S#Tx): Unit = if (direction) insert() else remove()
}

object EditGraphemeInsertObj {
  def apply[S <: Sys[S]](name: String, grapheme: Grapheme.Modifiable[S], time: LongObj[S], elem: Obj[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val timeH     = tx.newHandle(time)
    val graphemeH = tx.newHandle(grapheme)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, graphemeH, timeH, elemH)
    res.perform()
    res
  }

  private class Impl[S <: Sys[S]](name: String,
                                  graphemeH: stm.Source[S#Tx, Grapheme.Modifiable[S]],
                                  timeH: stm.Source[S#Tx, LongObj[S]],
                                  elemH: stm.Source[S#Tx, Obj[S]])(implicit cursor: stm.Cursor[S])
    extends EditGraphemeInsertRemoveObj[S](true, graphemeH, timeH, elemH) {

    override def getPresentationName: String = name
  }
}

object EditGraphemeRemoveObj {
  def apply[S <: Sys[S]](name: String, grapheme: Grapheme.Modifiable[S], time: LongObj[S], elem: Obj[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val timeH     = tx.newHandle(time)
    val graphemeH = tx.newHandle(grapheme)
    val elemH     = tx.newHandle(elem)
    val res = new Impl(name, graphemeH, timeH, elemH)
    res.perform()
    res
  }

  private class Impl[S <: Sys[S]](name: String, graphemeH: stm.Source[S#Tx, Grapheme.Modifiable[S]],
                                  timeH: stm.Source[S#Tx, LongObj[S]],
                                  elemH: stm.Source[S#Tx, Obj[S]])(implicit cursor: stm.Cursor[S])
    extends EditGraphemeInsertRemoveObj[S](false, graphemeH, timeH, elemH) {

    override def getPresentationName: String = name
  }
}