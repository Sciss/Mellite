package de.sciss
package mellite
package gui
package impl

import de.sciss.synth.proc.{FadeSpec, ProcKeys, Proc, Attribute, Sys}
import java.awt.Cursor
import de.sciss.span.{SpanLike, Span}
import de.sciss.synth.expr.ExprImplicits
import de.sciss.synth.{linShape, curveShape, proc}
import de.sciss.lucre.expr.Expr

final class TrackFadeToolImpl[S <: Sys[S]](protected val canvas: TimelineProcCanvas[S])
  extends BasicTrackRegionTool[S, TrackTool.Fade] {

  import TrackTool._

  def defaultCursor = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
  val name          = "Fade"
  val icon          = TrackToolsImpl.getIcon("fade")

  private var dragCurve = false

  protected def dragToParam(d: Drag): Fade = {
    val firstSpan = d.firstRegion.span
    val leftHand = firstSpan match {
      case Span(start, stop)  => math.abs(d.firstPos - start) < math.abs(d.firstPos - stop)
      case Span.From(start)   => true
      case Span.Until(stop)   => false
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

  protected def commitProc(drag: Fade)(span: Expr[S, SpanLike], proc: Proc[S])(implicit tx: S#Tx) {
    import drag._

    val attr    = proc.attributes
    val exprIn  = attr[Attribute.FadeSpec](ProcKeys.attrFadeIn )
    val exprOut = attr[Attribute.FadeSpec](ProcKeys.attrFadeOut)
    val valIn   = exprIn .map(_.value).getOrElse(EmptyFade)
    val valOut  = exprOut.map(_.value).getOrElse(EmptyFade)
    val total   = span.value match {
      case Span(start, stop)  => stop - start
      case _                  => Long.MaxValue
    }

    val dIn     = math.max(-valIn.numFrames, math.min(total - (valIn.numFrames + valOut.numFrames), deltaFadeIn))
    val valInC  = valIn.shape match {
      case `linShape`               => 0f
      case `curveShape`(curvature)  => curvature
      case _                        => Float.NaN
    }
    val dInC    = if (valInC.isNaN) 0f else math.max(-20, math.min(20, deltaFadeInCurve + valInC)) - valInC

    val newValIn = if (dIn != 0L || dInC != 0f) {
      val newInC  = valInC + dInC
      val shape   = if (newInC == 0f) linShape else curveShape(newInC)
      val fr      = valIn.numFrames + dIn
      val res     = FadeSpec.Value(fr, shape, valIn.floor)
      val elem    = FadeSpec.Elem.newConst[S](res)
      exprIn match {
        case Some(Expr.Var(vr)) =>
          vr() = elem
          res

        case None =>
          val vr = FadeSpec.Elem.newVar(elem)
          attr.put(ProcKeys.attrFadeIn, Attribute.FadeSpec(vr))
          res

        case _ =>
          valIn
      }
    } else valIn

    // XXX TODO: DRY
    val dOut    = math.max(-valOut.numFrames, math.min(total - newValIn.numFrames, deltaFadeOut))
    val valOutC = valOut.shape match {
      case `linShape`               => 0f
      case `curveShape`(curvature)  => curvature
      case _                        => Float.NaN
    }
    val dOutC    = if (valOutC.isNaN) 0f else math.max(-20, math.min(20, deltaFadeOutCurve + valOutC)) - valOutC

    if (dOut != 0L || dOutC != 0f) {
      val newOutC = valOutC + dOutC
      val shape   = if (newOutC == 0f) linShape else curveShape(newOutC)
      val fr      = valOut.numFrames + dOut
      val res     = FadeSpec.Value(fr, shape, valOut.floor)
      val elem    = FadeSpec.Elem.newConst[S](res)
      exprOut match {
        case Some(Expr.Var(vr)) =>
          vr() = elem

        case None =>
          val vr  = FadeSpec.Elem.newVar(elem)
          attr.put(ProcKeys.attrFadeOut, Attribute.FadeSpec(vr))

        case _ =>
      }
    }
  }

  protected def dialog() = None // XXX TODO
}
