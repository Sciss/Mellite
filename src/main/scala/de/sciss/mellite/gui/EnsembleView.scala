/*
 *  EnsembleView.scala
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
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.document.{EnsembleViewImpl => Impl}
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Ensemble, Transport, Universe}

object EnsembleView {
  def apply[S <: Sys[S]](ensemble: Ensemble[S])(implicit tx: S#Tx, universe: Universe[S],
                                                undoManager: UndoManager): EnsembleView[S] =
    Impl(ensemble)
}
trait EnsembleView[S <: Sys[S]] extends View.Editable[S] with UniverseView[S] with CanBounce {
  def folderView: FolderView[S]

  def ensemble(implicit tx: S#Tx): Ensemble[S]

  def transport: Transport[S]
}