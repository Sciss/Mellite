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
import de.sciss.lucre.ArtifactLocation
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.artifact.{ArtifactLocationFrameImpl => Impl}
import de.sciss.synth.proc.Universe

object ArtifactLocationFrame {
  def apply[T <: Txn[T]](obj: ArtifactLocation[T])
                        (implicit tx: T, universe: Universe[T]): ArtifactLocationFrame[T] =
    Impl(obj)
}

trait ArtifactLocationFrame[T <: Txn[T]] extends lucre.swing.Window[T] {
  def view: ArtifactLocationView[T]
}
