/*
 *  ArtifactFrame.scala
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

import de.sciss.desktop.FileDialog
import de.sciss.lucre
import de.sciss.lucre.Artifact
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.artifact.ArtifactFrameImpl
import de.sciss.proc.Universe

object ArtifactFrame {
  def apply[T <: Txn[T]](obj: Artifact[T], mode: Boolean, initMode: FileDialog.Mode = FileDialog.Save)
                        (implicit tx: T, universe: Universe[T]): ArtifactFrame[T] =
    ArtifactFrameImpl(obj, mode = mode, initMode = initMode)
}

trait ArtifactFrame[T <: Txn[T]] extends lucre.swing.Window[T] {
  def view: ArtifactView[T]
}
