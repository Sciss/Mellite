/*
 *  FScapeOutputsView.scala
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
import de.sciss.fscape.lucre.FScape
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.DragAndDrop.Flavor
import de.sciss.mellite.gui.impl.fscape.FScapeOutputsViewImpl
import de.sciss.synth.proc.Universe
import de.sciss.synth.proc.gui.UniverseView

object FScapeOutputsView {
  final case class Drag[S <: Sys[S]](universe: Universe[S], fscape: stm.Source[S#Tx, FScape[S]], key: String)

  final val flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor

  def apply[S <: Sys[S]](obj: FScape[S])(implicit tx: S#Tx, universe: Universe[S],
                                         undoManager: UndoManager): FScapeOutputsView[S] =
    FScapeOutputsViewImpl(obj)
}
trait FScapeOutputsView[S <: Sys[S]] extends UniverseView[S] with View.Editable[S]