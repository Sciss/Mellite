/*
 *  LinkTargetTimeline.scala
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

import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.edit.EditTimelineRemoveObj
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import javax.swing.undo.UndoableEdit

final class LinkTargetTimeline[S <: Sys[S]](val attr: InputAttrTimeline[S],
                                            spanH: stm.Source[S#Tx, SpanLikeObj[S]],
                                            objH : stm.Source[S#Tx, Obj[S]])
  extends LinkTarget[S] {

  override def toString: String = s"LinkTargetTimeline($attr)@${hashCode.toHexString}"

  def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    val tl = attr.timeline
    tl.modifiableOption.map { tlMod =>
      val span = spanH()
      val obj  = objH()
      EditTimelineRemoveObj("Remove Proc", tlMod, span, obj)
    }
  }
}
