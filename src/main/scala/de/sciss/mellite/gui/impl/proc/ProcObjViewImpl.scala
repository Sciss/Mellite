/*
 *  ProcObjViewImpl.scala
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
package gui.impl.proc

import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{CodeFrame, ListObjView, ObjView}
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, ObjViewImpl}
import de.sciss.synth.proc.{Proc, Universe}

trait ProcObjViewImpl[S <: Sys[S]]
  extends ListObjView[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.EmptyRenderer[S]
    with ListObjViewImpl.NonEditable[S]
    with ProcObjView[S] {

  override def obj(implicit tx: S#Tx): Proc[S] = objH()

  final def factory   : ObjView.Factory = ProcObjView
  final def isViewable: Boolean         = true

  // currently this just opens a code editor. in the future we should
  // add a scans map editor, and a convenience button for the attributes
  final def openView(parent: Option[Window[S]])
                    (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
    import de.sciss.mellite.Mellite.compiler
    val frame = CodeFrame.proc(obj)
    Some(frame)
  }
}

