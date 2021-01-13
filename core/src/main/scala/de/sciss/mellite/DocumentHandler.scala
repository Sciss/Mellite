/*
 *  DocumentHandler.scala
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

package de.sciss.mellite

import java.net.URI

import de.sciss.lucre.Txn
import de.sciss.mellite.impl.DocumentHandlerImpl
import de.sciss.model.Model
import de.sciss.proc.Universe

object DocumentHandler {

  type Document = Universe[_] // Universe[_ <: Txn[_]]

  lazy val instance: DocumentHandler =
    DocumentHandlerImpl()

  sealed trait Update
  final case class Opened[T <: Txn[T]](u: Universe[T]) extends Update
  final case class Closed[T <: Txn[T]](u: Universe[T]) extends Update
}

/** Note: the model dispatches not on the EDT. Listeners
  * requiring to execute code on the EDT should use a
  * wrapper like `defer` (LucreSwing).
  */
trait DocumentHandler extends Model[DocumentHandler.Update] {
  import DocumentHandler.Document

  def addDocument[T <: Txn[T]](universe: Universe[T])(implicit tx: T): Unit

  def allDocuments: Iterator[Document]
  def getDocument(folder: URI): Option[Document]

  def isEmpty: Boolean
}