/*
 *  NuagesEditorFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.document.{NuagesEditorFrameImpl => Impl}
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Universe

object NuagesEditorFrame {
  def apply[S <: Sys[S]](obj: Nuages[S])(implicit tx: S#Tx, universe: Universe[S]): NuagesEditorFrame[S] =
    Impl[S](obj)
}
trait NuagesEditorFrame[S <: Sys[S]] extends Window[S] {
  override def view: NuagesEditorView[S]
}