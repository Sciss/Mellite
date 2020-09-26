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
import de.sciss.lucre.Artifact
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.artifact.{ArtifactViewImpl => Impl}
import de.sciss.synth.proc.Universe

object ArtifactView {
  def apply[T <: Txn[T]](obj: Artifact[T], mode: Boolean, initMode: FileDialog.Mode)
                        (implicit tx: T, universe: Universe[T],
                                           undo: UndoManager): ArtifactView[T] =
    Impl(obj, mode = mode, initMode = initMode)
}
trait ArtifactView[T <: Txn[T]] extends UniverseView[T] with View.Editable[T]
