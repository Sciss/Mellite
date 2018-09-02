/*
 *  FScapeOutputsView.scala
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

package de.sciss.mellite.gui

import de.sciss.desktop.UndoManager
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.DragAndDrop.Flavor
import de.sciss.mellite.gui.impl.fscape.FScapeOutputsViewImpl
import de.sciss.synth.proc.Workspace

object FScapeOutputsView {
  final case class Drag[S <: Sys[S]](workspace: Workspace[S], fscape: stm.Source[S#Tx, FScape[S]], key: String)

  final val flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  def apply[S <: Sys[S]](obj: FScape[S])(implicit tx: S#Tx, cursor: stm.Cursor[S],
                                       workspace: Workspace[S], undoManager: UndoManager): FScapeOutputsView[S] =
    FScapeOutputsViewImpl(obj)
}
trait FScapeOutputsView[S <: Sys[S]] extends ViewHasWorkspace[S] with View.Editable[S]