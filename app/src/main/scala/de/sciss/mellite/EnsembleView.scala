/*
 *  EnsembleView.scala
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
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.document.{EnsembleViewImpl => Impl}
import de.sciss.synth.proc.{Ensemble, Universe}

object EnsembleView {
  def apply[S <: Sys[S]](ensemble: Ensemble[S])(implicit tx: S#Tx, universe: Universe[S],
                                                undoManager: UndoManager): EnsembleView[S] =
    Impl(ensemble)
}
trait EnsembleView[S <: Sys[S]] extends View.Editable[S] with UniverseView[S] with CanBounce {
  def folderView: FolderView[S]

  def ensemble(implicit tx: S#Tx): Ensemble[S]

//  def transport: Transport[S]
}