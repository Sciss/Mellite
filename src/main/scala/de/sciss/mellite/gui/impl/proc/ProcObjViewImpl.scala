package de.sciss.mellite.gui.impl.proc

import de.sciss.lucre.stm
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{CodeFrame, ListObjView, ObjView}
import de.sciss.mellite.gui.impl.{ListObjViewImpl, ObjViewImpl}
import de.sciss.synth.proc.{Proc, Workspace}

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
                    (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): Option[Window[S]] = {
    import de.sciss.mellite.Mellite.compiler
    val frame = CodeFrame.proc(obj)
    Some(frame)
  }
}

