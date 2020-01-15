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
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Timeline, Universe}

import scala.swing.Table

object GlobalProcsView /*extends GlobalProcsView.Factory*/ {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[S <: Sys[S]](group: Timeline[S], selectionModel: SelectionModel[S, ObjTimelineView[S]])
                          (implicit tx: S#Tx, universe: Universe[S],
                           undoManager: UndoManager): GlobalProcsView[S]
  }

  def apply[S <: Sys[S]](group: Timeline[S], selectionModel: SelectionModel[S, ObjTimelineView[S]])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undoManager: UndoManager): GlobalProcsView[S] =
    companion(group, selectionModel)
}
trait GlobalProcsView[S <: stm.Sys[S]] extends UniverseView[S] with View.Editable[S] {
  def tableComponent: Table

//  def selectionModel: SelectionModel[S, ProcObjView.Timeline[S]]
  def selectionModel: SelectionModel[S, ObjView[S]]

//  def iterator: Iterator[ProcObjView.Timeline[S]]
  def iterator: Iterator[ObjView[S]]

//  def add    (proc: ProcObjView.Timeline[S]): Unit
//  def remove (proc: ProcObjView.Timeline[S]): Unit
//  def updated(proc: ProcObjView.Timeline[S]): Unit

  def add    (proc: ObjView[S]): Unit
  def remove (proc: ObjView[S]): Unit
  def updated(proc: ObjView[S]): Unit
}