/*
 *  ArtifactLocationFrame.scala
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

import de.sciss.lucre
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.artifact.{ArtifactLocationFrameImpl => Impl}
import de.sciss.synth.proc.Universe

object ArtifactLocationFrame {
  def apply[S <: Sys[S]](obj: ArtifactLocation[S])
                        (implicit tx: S#Tx, universe: Universe[S]): ArtifactLocationFrame[S] =
    Impl(obj)
}

trait ArtifactLocationFrame[S <: Sys[S]] extends lucre.swing.Window[S] {
  def view: ArtifactLocationView[S]
}
