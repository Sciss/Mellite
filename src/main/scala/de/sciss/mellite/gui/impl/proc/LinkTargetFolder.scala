/*
 *  LinkTargetFolder.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui.impl.proc

import javax.swing.undo.UndoableEdit

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditFolderRemoveObj
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget

final class LinkTargetFolder[S <: Sys[S]](val attr: InputAttrFolder[S],
                                          objH : stm.Source[S#Tx, Obj[S]])
  extends LinkTarget[S] {

  override def toString: String = s"LinkTargetFolder($attr)@${hashCode.toHexString}"

  def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    val f     = attr.folder
    val obj   = objH()
    val index = f.indexOf(obj)
    if (index < 0) None else {
      val edit = EditFolderRemoveObj("Proc", f, index = index, child = obj)
      Some(edit)
    }
  }
}
