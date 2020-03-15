/*
 *  ArtifactLocationView.scala
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
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.artifact.{ArtifactLocationViewImpl => Impl}
import de.sciss.synth.proc.Universe

object ArtifactLocationView {
  def apply[S <: Sys[S]](obj: ArtifactLocation[S])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undo: UndoManager): ArtifactLocationView[S] =
    Impl(obj)
}
trait ArtifactLocationView[S <: Sys[S]] extends UniverseView[S] with View.Editable[S]
