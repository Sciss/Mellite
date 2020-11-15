/*
 *  ProcObjViewImpl.scala
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

package de.sciss.mellite.impl.proc

import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{CodeFrame, ObjListView, ObjView}
import de.sciss.mellite.impl.objview.{ObjListViewImpl, ObjViewImpl}
import de.sciss.proc.Universe

trait ProcObjViewImpl[T <: Txn[T]]
  extends ObjListView[T]
    with ObjViewImpl.Impl[T]
    with ObjListViewImpl.EmptyRenderer[T]
    with ObjListViewImpl.NonEditable[T]
    with ProcObjView[T] {

  final def factory   : ObjView.Factory = ProcObjView
  final def isViewable: Boolean         = true

  // currently this just opens a code editor. in the future we should
  // add a scans map editor, and a convenience button for the attributes
  final def openView(parent: Option[Window[T]])
                    (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
    import de.sciss.mellite.Mellite.compiler
    val frame = CodeFrame.proc(obj)
    Some(frame)
  }
}

