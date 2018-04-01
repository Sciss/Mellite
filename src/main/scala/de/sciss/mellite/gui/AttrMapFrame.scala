/*
 *  AttrMapFrame.scala
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
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.document.{AttrMapFrameImpl => Impl}
import de.sciss.synth.proc.Workspace

object AttrMapFrame {
  def apply[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                      cursor: stm.Cursor[S]): AttrMapFrame[S] =
    Impl(obj)
}
trait AttrMapFrame[S <: Sys[S]] extends Window[S] {
  def contents: AttrMapView[S]  // XXX TODO - should really be `view`
}