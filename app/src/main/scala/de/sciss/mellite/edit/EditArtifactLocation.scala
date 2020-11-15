/*
 *  EditArtifactLocation.scala
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

import java.net.URI

import de.sciss.lucre.{ArtifactLocation, Cursor, Source, Txn}
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

object EditArtifactLocation {
  def apply[T <: Txn[T]](obj: ArtifactLocation.Var[T], directory: URI)
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val before    = obj.directory
    val objH      = tx.newHandle(obj)
    val res       = new Impl(objH, before, directory)
    res.perform()
    res
  }

  private[edit] final class Impl[T <: Txn[T]](objH  : Source[T, ArtifactLocation.Var[T]],
                                              before: URI, now: URI)(implicit cursor: Cursor[T])
    extends AbstractUndoableEdit {

    override def undo(): Unit = {
      super.undo()
      cursor.step { implicit tx => perform(before) }
    }

    override def redo(): Unit = {
      super.redo()
      cursor.step { implicit tx => perform() }
    }

    private def perform(directory: URI)(implicit tx: T): Unit =
      objH().update(ArtifactLocation.newConst(directory)) // .directory = directory

    def perform()(implicit tx: T): Unit = perform(now)

    override def getPresentationName = "Change Artifact Location"
  }
}
