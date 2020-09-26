/*
 *  FadeImpl.scala
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

import java.awt.Cursor

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{GUI, TimelineTool, TimelineTrackCanvas}
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.Shapes
import de.sciss.span.Span
import de.sciss.synth.Curve
import de.sciss.synth.proc.{FadeSpec, ObjKeys, Timeline}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

final class FadeImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T])
  extends BasicTimelineTool[T, TimelineTool.Fade] {

  import TimelineTool.{EmptyFade, Fade}

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
  val name                  = "Fade"
  val icon: Icon            = GUI.iconNormal(Shapes.Aperture) // ToolsImpl.getIcon("fade")

  private var dragCurve = false

  protected def dragToParam(d: Drag): Fade = {
    val firstSpan = d.initial.spanValue
    val leftHand = firstSpan match {
      case Span(start, stop)  => math.abs(d.firstPos - start) < math.abs(d.firstPos - stop)
      case Span.From (_)      => true
      case Span.Until(_)      => false
      case _                  => true
    }
    val (deltaTime, deltaCurve) = if (dragCurve) {
      val dc = (d.firstEvent.getY - d.currentEvent.getY) * 0.1f
      (0L, if (leftHand) -dc else dc)
    } else {
      (if (leftHand) d.currentPos - d.firstPos else d.firstPos - d.currentPos, 0f)
    }
    if (leftHand) Fade(deltaTime, 0L, deltaCurve, 0f)
    else Fade(0L, deltaTime, 0f, deltaCurve)
  }

  override protected def dragStarted(d: this.Drag): Boolean = {
    val result = super.dragStarted(d)
    if (result) {
      dragCurve = math.abs(d.currentEvent.getX - d.firstEvent.getX) <
        math.abs(d.currentEvent.getY - d.firstEvent.getY)
    }
    result
  }

  protected def commitObj(drag: Fade)(span: SpanLikeObj[T], obj: Obj[T], timeline: Timeline[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    import drag._

    val attr    = obj.attr
    val exprIn  = attr.$[FadeSpec.Obj](ObjKeys.attrFadeIn )
    val exprOut = attr.$[FadeSpec.Obj](ObjKeys.attrFadeOut)
    val valIn   = exprIn .fold(EmptyFade)(_.value)
    val valOut  = exprOut.fold(EmptyFade)(_.value)
    val total   = span.value match {
      case Span(start, stop)  => stop - start
      case _                  => Long.MaxValue
    }

    val dIn     = math.max(-valIn.numFrames, math.min(total - (valIn.numFrames + valOut.numFrames), deltaFadeIn))
    val valInC  = valIn.curve match {
      case Curve.linear                 => 0f
      case Curve.parametric(curvature)  => curvature
      case _                            => Float.NaN
    }
    val dInC    = if (valInC.isNaN) 0f else math.max(-20, math.min(20, deltaFadeInCurve + valInC)) - valInC

    var edits = List.empty[UndoableEdit]

    val newValIn = if (dIn != 0L || dInC != 0f) {
      val newInC  = valInC + dInC
      val curve   = if (newInC == 0f) Curve.linear else Curve.parametric(newInC)
      val fr      = valIn.numFrames + dIn
      val res     = FadeSpec(fr, curve, valIn.floor)
      val elem    = FadeSpec.Obj.newConst[T](res)

      val edit    = EditAttrMap.expr[T, FadeSpec, FadeSpec.Obj]("Adjust Fade-In", obj, ObjKeys.attrFadeIn, Some(elem))

      edits ::= edit

      //      exprIn match {
      //        case Some(Expr.Var(vr)) =>
      //          vr() = elem
      //          res
      //
      //        case None =>
      //          val vr = FadeSpec.Expr.newVar(elem)
      //          attr.put(ObjKeys.attrFadeIn, Obj(FadeSpec.Obj(vr)))
      //          res
      //
      //        case _ =>
      //          valIn
      //      }
      res

    } else valIn

    // XXX TODO: DRY
    val dOut    = math.max(-valOut.numFrames, math.min(total - newValIn.numFrames, deltaFadeOut))
    val valOutC = valOut.curve match {
      case Curve.linear                 => 0f
      case Curve.parametric(curvature)  => curvature
      case _                            => Float.NaN
    }
    val dOutC    = if (valOutC.isNaN) 0f else math.max(-20, math.min(20, deltaFadeOutCurve + valOutC)) - valOutC

    if (dOut != 0L || dOutC != 0f) {
      val newOutC = valOutC + dOutC
      val curve   = if (newOutC == 0f) Curve.linear else Curve.parametric(newOutC)
      val fr      = valOut.numFrames + dOut
      val res     = FadeSpec(fr, curve, valOut.floor)
      val elem    = FadeSpec.Obj.newConst[T](res)
      val edit    = EditAttrMap.expr[T, FadeSpec, FadeSpec.Obj]("Adjust Fade-Out", obj, ObjKeys.attrFadeOut, Some(elem))

      edits ::= edit

      //      exprOut match {
      //        case Some(Expr.Var(vr)) =>
      //          vr() = elem
      //
      //        case None =>
      //          val vr  = FadeSpec.Expr.newVar(elem)
      //          attr.put(ObjKeys.attrFadeOut, Obj(FadeSpec.Obj(vr)))
      //
      //        case _ =>
      //      }
    }

    CompoundEdit(edits,s"Adjust $name")
  }

  protected def dialog(): Option[TimelineTool.Fade] = None // XXX TODO
}
