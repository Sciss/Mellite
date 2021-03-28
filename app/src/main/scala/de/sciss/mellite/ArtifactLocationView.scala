/*
 *  ArtifactLocationView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.desktop.UndoManager
import de.sciss.lucre.ArtifactLocation
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.artifact.{ArtifactLocationViewImpl => Impl}
import de.sciss.proc.Universe

object ArtifactLocationView {
  def apply[T <: Txn[T]](obj: ArtifactLocation[T])
                        (implicit tx: T, universe: Universe[T],
                         undo: UndoManager): ArtifactLocationView[T] =
    Impl(obj)
}
trait ArtifactLocationView[T <: Txn[T]] extends UniverseObjView[T] with View.Editable[T] {
  override def obj(implicit tx: T): ArtifactLocation[T]
}