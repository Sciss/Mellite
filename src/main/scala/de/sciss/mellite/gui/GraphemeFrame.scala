/*
 *  GraphemeFrame.scala
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

import de.sciss.lucre
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Grapheme, Workspace}
import impl.grapheme.{GraphemeFrameImpl => Impl}

object GraphemeFrame {
  def apply[S <: Sys[S]](group: Grapheme[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): GraphemeFrame[S] =
    Impl(group)
}
trait GraphemeFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  def view: GraphemeView[S]
}