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
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.artifact.{ArtifactFrameImpl => Impl}
import de.sciss.synth.proc.Universe

object ArtifactFrame {
  def apply[S <: Sys[S]](obj: Artifact[S], mode: Boolean, initMode: FileDialog.Mode = FileDialog.Save)
                        (implicit tx: S#Tx, universe: Universe[S]): ArtifactFrame[S] =
    Impl(obj, mode = mode, initMode = initMode)
}

trait ArtifactFrame[S <: Sys[S]] extends lucre.swing.Window[S] {
  def view: ArtifactView[S]
}
