/*
 *  WorkspaceCache.scala
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

import de.sciss.lucre.Txn.peer
import de.sciss.lucre.{AnyTxn, Disposable, Txn, TxnLike, Workspace}

import scala.concurrent.stm.TMap

object WorkspaceCache {
  def apply[A](): WorkspaceCache[A] = new Impl

  private final class Value[T <: Txn[T], A](impl: Impl[A], val value: A)
                                           (implicit workspace: Workspace[T], tx: T)
    extends Disposable[T] {

    workspace.addDependent(this)

    def dispose()(implicit tx: T): Unit = impl.remove()
  }

  private final class Impl[A] extends WorkspaceCache[A] {

    private[this] val map = TMap.empty[Workspace[_], Value[_, A]]

    def apply[T <: Txn[T]](value: => A)(implicit tx: T, workspace: Workspace[T]): A = {
      val res = map.get(workspace).getOrElse {
        val res0 = new Value(this, value)
        map.put(workspace, res0)
        res0
      }
      res.value
    }

    def get[T <: Txn[T]]()(implicit tx: T, workspace: Workspace[T]): Option[A] =
      map.get(workspace).map(_.value)

    def set[T <: Txn[T]](value: A)(implicit tx: T, workspace: Workspace[T]): Option[A] = {
      val vNew = new Value(this, value)
      map.put(workspace, vNew).map { vOld =>
        removeValue(workspace, vOld)
        vOld.value
      }
    }

    def remove[T <: Txn[T]]()(implicit tx: T, workspace: Workspace[T]): Boolean = {
      val opt = map.remove(workspace)
      opt.foreach { v0 =>
        val v = v0.asInstanceOf[Disposable[T]]
        workspace.removeDependent(v)
      }
      opt.isDefined
    }

    private def removeValue(workspace0: Workspace[_], v0: Value[_, A])(implicit tx: TxnLike): Unit = {
      val workspace = workspace0.cast[AnyTxn]
      val v         = v0.asInstanceOf[Disposable[AnyTxn]]
      workspace.removeDependent(v)
    }

    def dispose()(implicit tx: TxnLike): Unit = {
      map.foreach {
        case (workspace0, v0) => removeValue(workspace0, v0)
      }
      map.clear()
    }
  }
}
trait WorkspaceCache[A] extends Disposable[TxnLike] {
  def apply[T <: Txn[T]](value: => A)(implicit tx: T, workspace: Workspace[T]): A

  def set[T <: Txn[T]](value: A)(implicit tx: T, workspace: Workspace[T]): Option[A]

  def get[T <: Txn[T]]()(implicit tx: T, workspace: Workspace[T]): Option[A]

  def remove[T <: Txn[T]]()(implicit tx: T, workspace: Workspace[T]): Boolean
}