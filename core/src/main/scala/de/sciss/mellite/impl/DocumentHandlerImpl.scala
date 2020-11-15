/*
 *  DocumentHandlerImpl.scala
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

import java.net.URI

import de.sciss.lucre.Txn.peer
import de.sciss.lucre.{Disposable, Txn, TxnLike}
import de.sciss.mellite.DocumentHandler
import de.sciss.mellite.DocumentHandler.Document
import de.sciss.model.impl.ModelImpl
import de.sciss.proc.Universe

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.{TMap, Ref => STMRef}

object DocumentHandlerImpl {

  def apply(): DocumentHandler = new Impl

  private final class Impl extends DocumentHandler with ModelImpl[DocumentHandler.Update] {
    override def toString = "DocumentHandler"

    private val all   = STMRef(Vec.empty[Universe[_] /*Document*/])
    private val map   = TMap.empty[URI, Universe[_]] // Document] // path to document

    // def openRead(path: String): Document = ...

    def addDocument[T <: Txn[T]](u: Universe[T])(implicit tx: T): Unit = {
      u.workspace.folder.foreach { p =>
        require(!map.contains(p), s"Workspace for path '$p' is already registered")
        map += p -> u
      }
      all.transform(_ :+ u)

      u.workspace.addDependent(new Disposable[T] {
        def dispose()(implicit tx: T): Unit = removeDoc(u)
      })

      deferTx {
        dispatch(DocumentHandler.Opened(u))
      }
    }

    private def deferTx(code: => Unit)(implicit tx: TxnLike): Unit = tx.afterCommit(code)

    private def removeDoc[T <: Txn[T]](u: Universe[T])(implicit tx: T): Unit = {
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
    def getDocument(file: URI): Option[Document] = map.single.get(file)

    def isEmpty: Boolean = map.single.isEmpty
  }
}