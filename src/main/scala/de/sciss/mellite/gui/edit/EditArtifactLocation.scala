/*
 *  EditArtifactLocation.scala
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

import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

import de.sciss.file.File
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.stm

object EditArtifactLocation {
  def apply[S <: Sys[S]](obj: ArtifactLocation.Var[S], directory: File)
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val before    = obj.directory
    val objH      = tx.newHandle(obj)
    val res       = new Impl(objH, before, directory)
    res.perform()
    res
  }

  private[edit] final class Impl[S <: Sys[S]](objH  : stm.Source[S#Tx, ArtifactLocation.Var[S]],
                                              before: File, now: File)(implicit cursor: stm.Cursor[S])
    extends AbstractUndoableEdit {

    override def undo(): Unit = {
      super.undo()
      cursor.step { implicit tx => perform(before) }
    }

    override def redo(): Unit = {
      super.redo()
      cursor.step { implicit tx => perform() }
    }

    private def perform(directory: File)(implicit tx: S#Tx): Unit =
      objH().update(ArtifactLocation.newConst(directory)) // .directory = directory

    def perform()(implicit tx: S#Tx): Unit = perform(now)

    override def getPresentationName = "Change Artifact Location"
  }
}
