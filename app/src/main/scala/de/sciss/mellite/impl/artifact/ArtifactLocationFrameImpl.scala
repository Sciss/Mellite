/*
 *  ArtifactLocationFrameImpl.scala
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

package de.sciss.mellite.impl.artifact

import de.sciss.desktop.UndoManager
import de.sciss.lucre.ArtifactLocation
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.ArtifactLocationObjView.humanName
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{ArtifactLocationFrame, ArtifactLocationView}
import de.sciss.proc.Universe

object ArtifactLocationFrameImpl {
  def apply[T <: Txn[T]](obj: ArtifactLocation[T])
                        (implicit tx: T, universe: Universe[T]): ArtifactLocationFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val afv       = ArtifactLocationView(obj)
    val name      = CellView.name(obj)
    val res       = new Impl(/* doc, */ view = afv, name = name)
    res.init()
    res
  }

  private final class Impl[T <: Txn[T]](/* val document: Workspace[T], */ val view: ArtifactLocationView[T],
                                        name: CellView[T, String])
    extends WindowImpl[T](name.map(n => s"$n : $humanName"))
      with ArtifactLocationFrame[T]
}