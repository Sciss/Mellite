/*
 *  GlobalProcsView.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.mellite.gui.impl.timeline.{GlobalProcsViewImpl => Impl}
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Timeline, Universe}

import scala.swing.Table

object GlobalProcsView {
  def apply[S <: Sys[S]](group: Timeline[S], selectionModel: SelectionModel[S, TimelineObjView[S]])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undoManager: UndoManager): GlobalProcsView[S] =
      Impl(group, selectionModel)
}
trait GlobalProcsView[S <: stm.Sys[S]] extends UniverseView[S] with View.Editable[S] {
  def tableComponent: Table

  def selectionModel: SelectionModel[S, ProcObjView.Timeline[S]]

  def iterator: Iterator[ProcObjView.Timeline[S]]

  def add    (proc: ProcObjView.Timeline[S]): Unit
  def remove (proc: ProcObjView.Timeline[S]): Unit
  def updated(proc: ProcObjView.Timeline[S]): Unit
}