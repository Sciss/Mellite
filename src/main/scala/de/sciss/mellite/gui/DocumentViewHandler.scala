/*
 *  DocumentViewHandler.scala
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

package de.sciss.mellite.gui

import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing._
import de.sciss.mellite.DocumentHandler
import de.sciss.mellite.gui.impl.document.{ViewHandlerImpl => Impl}
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
//    def apply[S <: Sys[S]](document: Workspace[S]): Iterator[DocumentView[S]] = {
//      requireEDT()
//      map(document).iterator.asInstanceOf[Iterator[DocumentView[S]]]
//    }
//
//    def add[S <: Sys[S]](view: DocumentView[S]): Unit = {
//      requireEDT()
//      map += view.document -> (map(view.document) :+ view)
//    }
//
//    def remove[S <: Sys[S]](view: DocumentView[S]): Unit = {
//      requireEDT()
//      val vec = map(view.document)
//      val idx = vec.indexOf(view)
//      require(idx >= 0, s"View $view was not registered")
//      map += view.document -> vec.patch(idx, Vec.empty, 1)
//    }
//  }
//}
//trait DocumentViewHandler /* extends Model... */ {
//  def apply [S <: Sys[S]](document: Workspace[S]): Iterator[DocumentView[S]]
//  def add   [S <: Sys[S]](view: DocumentView[S]): Unit
//  def remove[S <: Sys[S]](view: DocumentView[S]): Unit
//}

object DocumentViewHandler {
  type WorkspaceWindow[S <: Sys[S]] = Window[S] // MMM

  type View[S <: Sys[S]] = WorkspaceWindow[S]

  lazy val instance: DocumentViewHandler = Impl.instance

  sealed trait Update
  case class Activated[S <: Sys[S]](u: Universe[S]) extends Update
}
trait DocumentViewHandler extends Model[DocumentViewHandler.Update] {
  def getWindow[S <: Sys[S]](u: Universe[S]): Option[DocumentViewHandler.View[_]]
  // var activeDocument: Option[Document]
  def activeDocument: Option[DocumentHandler.Document]
  def activeDocument_=[S <: Sys[S]](u: Option[Universe[S]]): Unit
}