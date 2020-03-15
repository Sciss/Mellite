/*
 *  ArtifactLocationFrameImpl.scala
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

package de.sciss.mellite.impl.artifact

import de.sciss.desktop.UndoManager
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ArtifactLocationObjView.humanName
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{ArtifactLocationFrame, ArtifactLocationView}
import de.sciss.synth.proc.Universe

object ArtifactLocationFrameImpl {
  def apply[S <: Sys[S]](obj: ArtifactLocation[S])
                        (implicit tx: S#Tx, universe: Universe[S]): ArtifactLocationFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val afv       = ArtifactLocationView(obj)
    val name      = CellView.name(obj)
    val res       = new Impl(/* doc, */ view = afv, name = name)
    res.init()
    res
  }

  private final class Impl[S <: Sys[S]](/* val document: Workspace[S], */ val view: ArtifactLocationView[S],
                                        name: CellView[S#Tx, String])
    extends WindowImpl[S](name.map(n => s"$n : $humanName"))
      with ArtifactLocationFrame[S]
}