/*
 *  GainImpl.scala
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

package de.sciss.mellite.impl.timeline.tool

import java.awt

import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, DoubleObj, Obj, SpanLikeObj}
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.{GUI, Shapes, TimelineTool, TimelineTrackCanvas}
import de.sciss.synth
import de.sciss.synth.proc.{ObjKeys, Timeline}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class GainImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Gain] {

  import TimelineTool.Gain

  def defaultCursor: awt.Cursor = awt.Cursor.getPredefinedCursor(awt.Cursor.N_RESIZE_CURSOR)
  val name                  = "Gain"
  val icon: Icon            = GUI.iconNormal(Shapes.Gain) // ToolsImpl.getIcon("vresize")

  protected def dialog(): Option[Gain] = None // not yet supported

  override protected def dragStarted(d: Drag): Boolean =
    d.currentEvent.getY != d.firstEvent.getY

  protected def dragToParam(d: Drag): Gain = {
    val dy = d.firstEvent.getY - d.currentEvent.getY
    // use 0.1 dB per pixel. eventually we could use modifier keys...
    import synth._
    val factor = (dy.toFloat / 10).dbAmp
    Gain(factor)
  }

  protected def commitObj(drag: Gain)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    import drag.factor
    // ProcActions.adjustGain(obj, drag.factor)
    // val imp = ExprImplicits[T]
    if (factor == 1f) None else {
//      import expr.Ops._
      val newGain: DoubleObj[T] = obj.attr.$[DoubleObj](ObjKeys.attrGain) match {
        case Some(DoubleObj.Var(vr)) => vr() * factor.toDouble
        case other =>
          other.fold(1.0)(_.value) * factor
      }
      import de.sciss.equal.Implicits._
      val newGainOpt = if (newGain === DoubleObj.newConst[T](1.0)) None else Some(newGain)
      val edit = EditAttrMap.expr[T, Double, DoubleObj](s"Adjust $name", obj, ObjKeys.attrGain, newGainOpt)
      Some(edit)
    }
  }
}
