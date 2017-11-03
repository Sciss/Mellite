package de.sciss.mellite
package gui.impl.proc

import javax.swing.undo.UndoableEdit

import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditTimelineRemoveObj
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget

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
