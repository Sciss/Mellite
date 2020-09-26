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

import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, Source, SpanLikeObj}
import de.sciss.mellite.edit.EditTimelineRemoveObj
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import javax.swing.undo.UndoableEdit

final class LinkTargetTimeline[T <: Txn[T]](val attr: InputAttrTimeline[T],
                                            spanH: Source[T, SpanLikeObj[T]],
                                            objH : Source[T, Obj[T]])
  extends LinkTarget[T] {

  override def toString: String = s"LinkTargetTimeline($attr)@${hashCode.toHexString}"

  def remove()(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    val tl = attr.timeline
    tl.modifiableOption.map { tlMod =>
      val span = spanH()
      val obj  = objH()
      EditTimelineRemoveObj("Remove Proc", tlMod, span, obj)
    }
  }
}
