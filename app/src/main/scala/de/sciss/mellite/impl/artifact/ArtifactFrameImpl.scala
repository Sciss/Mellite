/*
 *  ArtifactFrameImpl.scala
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

import de.sciss.desktop.{FileDialog, UndoManager}
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.impl.objview.ArtifactObjView.humanName
import de.sciss.mellite.{ArtifactFrame, ArtifactView}
import de.sciss.synth.proc.Universe

object ArtifactFrameImpl {
  def apply[S <: Sys[S]](obj: Artifact[S], mode: Boolean, initMode: FileDialog.Mode)
                        (implicit tx: S#Tx, universe: Universe[S]): ArtifactFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val afv       = ArtifactView(obj, mode = mode, initMode = initMode)
    val name      = CellView.name(obj)
    val res       = new Impl(/* doc, */ view = afv, name = name)
    res.init()
    res
  }

  private final class Impl[S <: Sys[S]](/* val document: Workspace[S], */ val view: ArtifactView[S],
                                        name: CellView[S#Tx, String])
    extends WindowImpl[S](name.map(n => s"$n : $humanName"))
      with ArtifactFrame[S]
}