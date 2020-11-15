/*
 *  DocumentViewHandler.scala
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

package de.sciss.mellite

import de.sciss.lucre.swing._
import de.sciss.lucre.Txn
import de.sciss.mellite.impl.DocumentViewHandlerImpl
import de.sciss.model.Model
import de.sciss.synth.proc.Universe

//object DocumentViewHandler {
//  lazy val instance: DocumentViewHandler = new DocumentViewHandler {
//    private var map = Map.empty[Workspace[_], Vec[DocumentView[_]]] withDefaultValue Vec.empty
//
//    Desktop.addListener {
//      case Desktop.OpenFiles(_, files) => files.foreach(ActionOpenWorkspace.perform)
//    }
//
//    def apply[T <: Txn[T]](document: Workspace[T]): Iterator[DocumentView[T]] = {
//      requireEDT()
//      map(document).iterator.asInstanceOf[Iterator[DocumentView[T]]]
//    }
//
//    def add[T <: Txn[T]](view: DocumentView[T]): Unit = {
//      requireEDT()
//      map += view.document -> (map(view.document) :+ view)
//    }
//
//    def remove[T <: Txn[T]](view: DocumentView[T]): Unit = {
//      requireEDT()
//      val vec = map(view.document)
//      val idx = vec.indexOf(view)
//      require(idx >= 0, s"View $view was not registered")
//      map += view.document -> vec.patch(idx, Vec.empty, 1)
//    }
//  }
//}
//trait DocumentViewHandler /* extends Model... */ {
//  def apply [T <: Txn[T]](document: Workspace[T]): Iterator[DocumentView[T]]
//  def add   [T <: Txn[T]](view: DocumentView[T]): Unit
//  def remove[T <: Txn[T]](view: DocumentView[T]): Unit
//}

object DocumentViewHandler {
  type WorkspaceWindow[T <: Txn[T]] = Window[T] // MMM

  type View[T <: Txn[T]] = WorkspaceWindow[T]

  def init(): Unit = {
    instance
    ()
  }

  lazy val instance: DocumentViewHandler =
    DocumentViewHandlerImpl.instance

  sealed trait Update
  case class Activated[T <: Txn[T]](u: Universe[T]) extends Update
}
trait DocumentViewHandler extends Model[DocumentViewHandler.Update] {
  def getWindow[T <: Txn[T]](u: Universe[T]): Option[DocumentViewHandler.View[_]]
  // var activeDocument: Option[Document]
  def activeDocument: Option[DocumentHandler.Document]
  def activeDocument_=[T <: Txn[T]](u: Option[Universe[T]]): Unit
}