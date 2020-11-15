/*
 *  DocumentViewHandlerImpl.scala
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

package de.sciss.mellite.impl

import de.sciss.lucre.Txn
import de.sciss.lucre.swing.LucreSwing.requireEDT
import de.sciss.mellite.{DocumentHandler, DocumentViewHandler}
import de.sciss.model.impl.ModelImpl
import de.sciss.proc.Universe

import scala.concurrent.stm.TMap

object DocumentViewHandlerImpl {
  import DocumentViewHandler.WorkspaceWindow
  import de.sciss.mellite.DocumentHandler.Document

  def instance: DocumentViewHandler = impl

  private lazy val impl = new Impl

  private final class Impl extends DocumentViewHandler with ModelImpl[DocumentViewHandler.Update] {
    override def toString = "DocumentViewHandler"

    private val map     = TMap  .empty[Document, WorkspaceWindow[_]]
    private var _active = Option.empty[Document]

    def activeDocument: Option[DocumentHandler.Document] = _active
    def activeDocument_=[T <: Txn[T]](value: Option[Universe[T]]): Unit = {
      requireEDT()
      if (_active != value) {
        _active = value
        value.foreach { u =>
          dispatch(DocumentViewHandler.Activated(u))
        }
      }
    }

    def getWindow[T <: Txn[T]](u: Universe[T]): Option[WorkspaceWindow[T]] = {
      requireEDT()
      map.single.get(u).asInstanceOf[Option[WorkspaceWindow[T]]]
    }
  }
}