/*
 *  LinkTargetOutput.scala
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

import de.sciss.lucre.Cursor
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import javax.swing.undo.UndoableEdit

final class LinkTargetOutput[T <: Txn[T]](val attr: InputAttrOutput[T])
  extends LinkTarget[T] {

  override def toString: String = s"LinkTargetOutput($attr)@${hashCode.toHexString}"

  def remove()(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    // val out = attr.output
    val obj  = attr.parent.obj
    val edit = EditAttrMap.remove("Output", obj = obj, key = attr.key)
    Some(edit)
  }
}

