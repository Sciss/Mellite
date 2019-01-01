/*
 *  DocumentHandlerImpl.scala
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

package de.sciss.mellite
package impl

import de.sciss.file._
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Disposable, Sys, TxnLike}
import de.sciss.mellite.DocumentHandler.Document
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.Universe

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.{TMap, Ref => STMRef}

object DocumentHandlerImpl {

  def apply(): DocumentHandler = new Impl

  private final class Impl extends DocumentHandler with ModelImpl[DocumentHandler.Update] {
    override def toString = "DocumentHandler"

    private val all   = STMRef(Vec.empty[Document])
    private val map   = TMap.empty[File, Document] // path to document

    // def openRead(path: String): Document = ...

    def addDocument[S <: Sys[S]](u: Universe[S])(implicit tx: S#Tx): Unit = {
      u.workspace.folder.foreach { p =>
        require(!map.contains(p), s"Workspace for path '$p' is already registered")
        map += p -> u
      }
      all.transform(_ :+ u)

      u.workspace.addDependent(new Disposable[S#Tx] {
        def dispose()(implicit tx: S#Tx): Unit = removeDoc(u)
      })

      deferTx {
        dispatch(DocumentHandler.Opened(u))
      }
    }

    private def deferTx(code: => Unit)(implicit tx: TxnLike): Unit = tx.afterCommit(code)

    private def removeDoc[S <: Sys[S]](u: Universe[S])(implicit tx: S#Tx): Unit = {
      all.transform { in =>
        val idx = in.indexOf(u)
        require(idx >= 0, s"Workspace ${u.workspace.folder.fold("") { p => s"for path '$p'" }} was not registered")
        in.patch(idx, Nil, 1)
      } (tx.peer)
      u.workspace.folder.foreach(map -= _)

      deferTx {
        dispatch(DocumentHandler.Closed(u))
      }
    }

    def allDocuments: Iterator[Document] = all.single().iterator
    def getDocument(file: File): Option[Document] = map.single.get(file)

    def isEmpty: Boolean = map.single.isEmpty
  }
}