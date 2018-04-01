/*
 *  DocumentHandler.scala
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

package de.sciss.mellite

import de.sciss.file.File
import de.sciss.lucre.stm.Sys
import de.sciss.mellite.impl.{DocumentHandlerImpl => Impl}
import de.sciss.model.Model
import de.sciss.synth.proc.Workspace

object DocumentHandler {
  type Document = Workspace[_ <: Sys[_]]

  lazy val instance: DocumentHandler = Impl()

  sealed trait Update
  final case class Opened[S <: Sys[S]](doc: Workspace[S]) extends Update
  final case class Closed[S <: Sys[S]](doc: Workspace[S]) extends Update
}

/** Note: the model dispatches not on the EDT. Listeners
  * requiring to execute code on the EDT should use a
  * wrapper like `defer` (LucreSwing).
  */
trait DocumentHandler extends Model[DocumentHandler.Update] {
  import DocumentHandler.Document

  def addDocument[S <: Sys[S]](doc: Workspace[S])(implicit tx: S#Tx): Unit

  // def openRead(path: String): Document
  def allDocuments: Iterator[Document]
  def getDocument(folder: File): Option[Document]

  def isEmpty: Boolean
}