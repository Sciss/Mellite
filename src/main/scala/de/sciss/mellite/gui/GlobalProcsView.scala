/*
 *  GlobalProcsView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.synth.proc.ProcGroup
import de.sciss.lucre.stm.Disposable
import scala.swing.Component
import de.sciss.lucre.stm
import de.sciss.mellite.gui.impl.timeline.{GlobalProcsViewImpl => Impl, ProcView}
import de.sciss.lucre.synth.Sys

object GlobalProcsView {
  def apply[S <: Sys[S]](group: ProcGroup[S], selectionModel: ProcSelectionModel[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): GlobalProcsView[S] =
      Impl(group, selectionModel)
}
trait GlobalProcsView[S <: Sys[S]] extends Disposable[S#Tx] {
  def component: Component

  def add    (proc: ProcView[S]): Unit
  def remove (proc: ProcView[S]): Unit
  def updated(proc: ProcView[S]): Unit
}