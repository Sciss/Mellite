/*
 *  WorkspaceCache.scala
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

import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Disposable, NoSys, Sys, TxnLike, WorkspaceHandle}
import de.sciss.synth.proc.Workspace

import scala.concurrent.stm.TMap

object WorkspaceCache {
  def apply[A](): WorkspaceCache[A] = new Impl

  private final class Value[S <: Sys[S], A](impl: Impl[A], val value: A)
                                           (implicit workspace: WorkspaceHandle[S], tx: S#Tx)
    extends Disposable[S#Tx] {

    workspace.addDependent(this)

    def dispose()(implicit tx: S#Tx): Unit = impl.remove()
  }

  private final class Impl[A] extends WorkspaceCache[A] {

    private[this] val map = TMap.empty[WorkspaceHandle[_], Value[_, A]]

    def apply[S <: Sys[S]](value: => A)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): A = {
      val res = map.get(workspace).getOrElse {
        val res0 = new Value(this, value)
        map.put(workspace, res0)
        res0
      }
      res.value
    }

    def get[S <: Sys[S]]()(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Option[A] =
      map.get(workspace).map(_.value)

    def set[S <: Sys[S]](value: A)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Option[A] = {
      val vNew = new Value(this, value)
      map.put(workspace, vNew).map { vOld =>
        removeValue(workspace, vOld)
        vOld.value
      }
    }

    def remove[S <: Sys[S]]()(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Boolean = {
      val opt = map.remove(workspace)
      opt.foreach { v0 =>
        val v = v0.asInstanceOf[Disposable[S#Tx]]
        workspace.removeDependent(v)
      }
      opt.isDefined
    }

    private def removeValue(workspace0: WorkspaceHandle[_], v0: Value[_, A])(implicit tx: TxnLike): Unit = {
      val workspace = workspace0.asInstanceOf[Workspace[NoSys]]
      val v         = v0.asInstanceOf[Disposable[NoSys#Tx]]
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
  def apply[S <: Sys[S]](value: => A)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): A

  def set[S <: Sys[S]](value: A)(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Option[A]

  def get[S <: Sys[S]]()(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Option[A]

  def remove[S <: Sys[S]]()(implicit tx: S#Tx, workspace: WorkspaceHandle[S]): Boolean
}