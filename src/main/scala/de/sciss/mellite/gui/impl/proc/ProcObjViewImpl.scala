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

package de.sciss.mellite.gui.impl.proc

import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.gui.CodeFrame
import de.sciss.mellite.impl.objview.{ObjListViewImpl, ObjViewImpl}
import de.sciss.synth.proc.Universe

trait ProcObjViewImpl[S <: Sys[S]]
  extends ObjListView[S]
    with ObjViewImpl.Impl[S]
    with ObjListViewImpl.EmptyRenderer[S]
    with ObjListViewImpl.NonEditable[S]
    with ProcObjView[S] {

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

