/*
 *  ArtifactView.scala
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

import de.sciss.desktop.{FileDialog, UndoManager}
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.artifact.{ArtifactViewImpl => Impl}
import de.sciss.synth.proc.Universe

object ArtifactView {
  def apply[S <: Sys[S]](obj: Artifact[S], mode: Boolean, initMode: FileDialog.Mode)
                        (implicit tx: S#Tx, universe: Universe[S],
                                           undo: UndoManager): ArtifactView[S] =
    Impl(obj, mode = mode, initMode = initMode)
}
trait ArtifactView[S <: Sys[S]] extends UniverseView[S] with View.Editable[S]
