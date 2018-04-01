/*
 *  NuagesEditorFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.stm
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Workspace
import impl.document.{NuagesEditorFrameImpl => Impl}

object NuagesEditorFrame {
  def apply[S <: Sys[S]](obj: Nuages[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                         cursor: stm.Cursor[S]): NuagesEditorFrame[S] =
    Impl[S](obj)
}
trait NuagesEditorFrame[S <: Sys[S]] extends Window[S] {
  override def view: NuagesEditorView[S]
}