/*
 *  LinkTargetFolder.scala
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

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.edit.EditFolderRemoveObj
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import javax.swing.undo.UndoableEdit

final class LinkTargetFolder[T <: Txn[T]](val attr: InputAttrFolder[T],
                                          objH : Source[T, Obj[T]])
  extends LinkTarget[T] {

  override def toString: String = s"LinkTargetFolder($attr)@${hashCode.toHexString}"

  def remove()(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val f     = attr.folder
    val obj   = objH()
    val index = f.indexOf(obj)
    if (index < 0) None else {
      val edit = EditFolderRemoveObj("Proc", f, index = index, child = obj)
      Some(edit)
    }
  }
}
