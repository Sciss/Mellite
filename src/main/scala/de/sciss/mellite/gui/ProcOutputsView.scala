/*
 *  ProcOutputsView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.DragAndDrop.Flavor
import de.sciss.mellite.gui.impl.proc.OutputsViewImpl
import de.sciss.synth.proc.{Proc, Workspace}

object ProcOutputsView {
  final case class Drag[S <: Sys[S]](workspace: Workspace[S], proc: stm.Source[S#Tx, Proc[S]], key: String)

  final val flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  def apply[S <: Sys[S]](obj: Proc[S])(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                           workspace: Workspace[S], undoManager: UndoManager): ProcOutputsView[S] =
    OutputsViewImpl(obj)
}
trait ProcOutputsView[S <: Sys[S]] extends ViewHasWorkspace[S] with View.Editable[S]