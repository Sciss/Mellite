/*
 *  GlobalProcsView.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.synth.proc.{Timeline, Universe}

import scala.swing.Table

object GlobalProcsView /*extends GlobalProcsView.Factory*/ {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: Txn[T]](group: Timeline[T], selectionModel: SelectionModel[T, ObjTimelineView[T]])
                          (implicit tx: T, universe: Universe[T],
                           undoManager: UndoManager): GlobalProcsView[T]
  }

  def apply[T <: Txn[T]](group: Timeline[T], selectionModel: SelectionModel[T, ObjTimelineView[T]])
                        (implicit tx: T, universe: Universe[T],
                         undoManager: UndoManager): GlobalProcsView[T] =
    companion(group, selectionModel)
}
trait GlobalProcsView[T <: LTxn[T]] extends UniverseView[T] with View.Editable[T] {
  def tableComponent: Table

//  def selectionModel: SelectionModel[T, ProcObjView.Timeline[T]]
  def selectionModel: SelectionModel[T, ObjView[T]]

//  def iterator: Iterator[ProcObjView.Timeline[T]]
  def iterator: Iterator[ObjView[T]]

//  def add    (proc: ProcObjView.Timeline[T]): Unit
//  def remove (proc: ProcObjView.Timeline[T]): Unit
//  def updated(proc: ProcObjView.Timeline[T]): Unit

  def add    (proc: ObjView[T]): Unit
  def remove (proc: ObjView[T]): Unit
  def updated(proc: ObjView[T]): Unit
}