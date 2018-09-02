/*
 *  LinkTargetOutput.scala
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
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget

final class LinkTargetOutput[S <: Sys[S]](val attr: InputAttrOutput[S])
  extends LinkTarget[S] {

  override def toString: String = s"LinkTargetOutput($attr)@${hashCode.toHexString}"

  def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    // val out = attr.output
    val obj  = attr.parent.obj
    val edit = EditAttrMap.remove("Output", obj = obj, key = attr.key)
    Some(edit)
  }
}

