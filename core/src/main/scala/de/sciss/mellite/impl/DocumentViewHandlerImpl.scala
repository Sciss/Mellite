/*
 *  DocumentViewHandlerImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl

import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.LucreSwing.requireEDT
import de.sciss.mellite.{DocumentHandler, DocumentViewHandler}
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.Universe

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
    def activeDocument_=[S <: Sys[S]](value: Option[Universe[S]]): Unit = {
      requireEDT()
      if (_active != value) {
        _active = value
        value.foreach { u =>
          dispatch(DocumentViewHandler.Activated(u))
        }
      }
    }

    def getWindow[S <: Sys[S]](u: Universe[S]): Option[WorkspaceWindow[S]] = {
      requireEDT()
      map.single.get(u).asInstanceOf[Option[WorkspaceWindow[S]]]
    }
  }
}