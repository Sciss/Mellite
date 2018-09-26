/*
 *  TimelineObjViewBasicImpl.scala
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
package gui
package impl
package timeline

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.lucre.expr.{CellView, IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.deferTx
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.Curve

import scala.swing.Graphics2D

trait TimelineObjViewBasicImpl[S <: stm.Sys[S]] extends TimelineObjView[S] with ObjViewImpl.Impl[S] {
  var trackIndex  : Int = _
  var trackHeight : Int = _
  // var nameOption  : Option[String] = _
  var spanValue   : SpanLike = _
  var spanH       : stm.Source[S#Tx, SpanLikeObj[S]] = _

  protected var idH  : stm.Source[S#Tx, S#Id] = _

  def span(implicit tx: S#Tx): SpanLikeObj[S] = spanH()
  def id  (implicit tx: S#Tx): S#Id           = idH()

  def initAttrs(id: S#Id, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): this.type = {
    val attr      = obj.attr

    val trackIdxView = CellView.attr[S, Int, IntObj](attr, TimelineObjView.attrTrackIndex)
    disposables ::= trackIdxView.react { implicit tx => opt =>
      deferTx {
        trackIndex = opt.getOrElse(0)
      }
      fire(ObjView.Repaint(this))
    }
    trackIndex   = trackIdxView().getOrElse(0)

    val trackHView = CellView.attr[S, Int, IntObj](attr, TimelineObjView.attrTrackHeight)
    disposables ::= trackHView.react { implicit tx => opt =>
      deferTx {
        trackHeight = opt.getOrElse(TimelineView.DefaultTrackHeight)
      }
      fire(ObjView.Repaint(this))
    }
    trackHeight   = trackHView().getOrElse(TimelineView.DefaultTrackHeight)

    spanH         = tx.newHandle(span)
    spanValue     = span.value
    idH           = tx.newHandle(id)
    initAttrs(obj)
  }

  protected def paintInner(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering, selected: Boolean): Unit = ()

  /** These are updated by paintBack and will thus be valid in paintFront as well. */
  var px  = 0
  var py  = 0
  var pw  = 0
  var ph  = 0

  var pStart = 0L
  var pStop  = 0L

  /** Inner (after title bar) */
  protected var phi   = 0
  /** Inner (after title bar) */
  protected var pyi   = 0
  /** Clipped left */
  protected var px1c  = 0
  /** Clipped right */
  protected var px2c  = 0

  def paintBack(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit = {
    val selected  = tlv.selectionModel.contains(this)
    val move0     = r.ttMoveState
    if (!move0.copy) {
      updateBounds (g, tlv, r, move0, selected = selected)
      paintBackImpl(g, tlv, r,        selected = selected)
    } else {
      // cheesy work around to show both original and copy
      val move1 = TrackTool.NoMove
      updateBounds (g, tlv, r, move1, selected = selected)
      paintBackImpl(g, tlv, r,        selected = selected)
      updateBounds (g, tlv, r, move0, selected = selected)
      paintBackImpl(g, tlv, r,        selected = selected)
    }
  }

  private[this] def updateBounds(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering,
                                 moveState: TrackTool.Move, selected: Boolean): Unit = {
    val canvas          = tlv.canvas
    var x1              = -5
    val peer            = canvas.canvasComponent.peer
    val w               = peer.getWidth
    var x2              = w + 5

    import canvas.{frameToScreen, trackToScreen}
    import r.{clipRect, ttResizeState => resizeState}

    def adjustStart(start: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime + resizeState.deltaStart
        if (dt0 >= 0) dt0 else {
          val minStart = TimelineNavigation.minStart(canvas.timelineModel)
          math.max(minStart - start, dt0)
        }
      } else 0L

    def adjustStop(stop: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime + resizeState.deltaStop
        dt0
//        if (dt0 >= 0) dt0 else {
//          val minStart = TimelineNavigation.minStart(canvas.timelineModel)
//          dt0 // math.max(-(stop - total.start + TimelineView.MinDur), dt0)
//        }
      } else 0L

//    def adjustMove(start: Long): Long =
//      if (selected) {
//        val dt0 = moveState.deltaTime
//        if (dt0 >= 0) dt0 else {
//          val total = tlv.timelineModel.bounds
//          math.max(-(start - total.start), dt0)
//        }
//      } else 0L

    spanValue match {
      case Span(start, stop) =>
        val dStart    = adjustStart(start)
        val dStop     = adjustStop (stop )
        pStart        = start + dStart
        pStop         = stop  + dStop
        val newStop   = math.max(pStart + TimelineView.MinDur, pStop)
        x1            = frameToScreen(pStart ).toInt
        x2            = frameToScreen(newStop).toInt
        // move          = adjustMove(start)

      case Span.From(start) =>
        val dStart    = adjustStart(start)
        pStart        = start + dStart
        pStop         = Long.MaxValue
        x1            = frameToScreen(pStart).toInt
        // x2         = w + 5
        // move          = adjustMove(start)

      case Span.Until(stop) =>
        val dStop     = adjustStop(stop)
        pStart        = Long.MinValue
        pStop         = stop + dStop
        x2            = frameToScreen(pStop).toInt
      // start         = Long.MinValue
      // x1         = -5
      // move       = 0L

      case Span.All =>
       pStart         = Long.MinValue
       pStop          = Long.MaxValue
      // x1            = -5
      // x2            = w + 5
      // move            = 0L

      case _ => // don't draw Span.Void
        return
    }

    val pTrk  = if (selected) math.max(0, trackIndex + moveState.deltaTrack) else trackIndex
    py        = trackToScreen(pTrk)
    px        = x1
    pw        = x2 - x1
    ph        = trackToScreen(pTrk + trackHeight) - py

    // clipped coordinates
    px1c      = math.max(px +  1, clipRect.x - 2)
    px2c      = math.min(px + pw, clipRect.x + clipRect.width + 3)
  }

  private[this] def paintBackImpl(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering, selected: Boolean): Unit = {
    val canvas          = tlv.canvas
    val trackTools      = canvas.trackTools
    val regionViewMode  = trackTools.regionViewMode

    import canvas.framesToScreen
    import r.{ttFadeState => fadeState}

    if (px1c < px2c) {  // skip this if we are not overlapping with clip
      import r.{pntBackground, pntFadeFill, pntFadeOutline, pntNameDark, pntNameLight, pntNameShadowDark, pntNameShadowLight, pntRegionBackground, pntRegionBackgroundMuted, pntRegionBackgroundSelected, pntRegionOutline, pntRegionOutlineSelected, regionTitleBaseline, regionTitleHeight, shape1, shape2}

      if (regionViewMode != RegionViewMode.None) {
        g.translate(px, py)
        g.setPaint(if (selected) pntRegionOutlineSelected    else pntRegionOutline   )
        g.fillRoundRect(0, 0, pw, ph, 5, 5)
        g.setPaint(if (selected) pntRegionBackgroundSelected else pntRegionBackground)
        g.fillRoundRect(1, 1, pw - 2, ph - 2, 4, 4)
        g.translate(-px, -py)
      }
      g.setPaint(pntBackground)
      g.drawLine(px - 1, py, px - 1, py + ph - 1) // better distinguish directly neighbouring regions

      val hndl = regionViewMode match {
        case RegionViewMode.None       => 0
        case RegionViewMode.Box        => 1
        case RegionViewMode.TitledBox  => regionTitleHeight
      }

      phi           = ph - (hndl + 1)
      pyi           = py + hndl
      val clipOrig  = g.getClip
      g.clipRect(px + 1, pyi, pw - 2, phi)

      paintInner(g, tlv = tlv, r = r, selected = selected)

      // --- fades ---
      def paintFade(curve: Curve, fw: Float, y1: Float, y2: Float, x: Float, x0: Float): Unit = {
        import math.{log10, max}
        shape1.reset()
        shape2.reset()
        val vScale  = phi / -3f
        val y1s     = max(-3, log10(y1)) * vScale + pyi
        shape1.moveTo(x, y1s)
        shape2.moveTo(x, y1s)
        var xs = 4
        while (xs < fw) {
          val ys = max(-3, log10(curve.levelAt(xs / fw, y1, y2))) * vScale + pyi
          shape1.lineTo(x + xs, ys)
          shape2.lineTo(x + xs, ys)
          xs += 3
        }
        val y2s     = max(-3, log10(y2)) * vScale + pyi
        shape1.lineTo(x + fw, y2s)
        shape2.lineTo(x + fw, y2s)
        shape1.lineTo(x0, pyi)
        g.setPaint(pntFadeFill)
        g.fill    (shape1)
        g.setPaint(pntFadeOutline)
        g.draw    (shape2)
      }

      this match {
        case fv: TimelineObjView.HasFade if trackTools.fadeViewMode == FadeViewMode.Curve =>
          def adjustFade(in: Curve, deltaCurve: Float): Curve = in match {
            case Curve.linear                 => Curve.parametric(math.max(-20, math.min(20, deltaCurve)))
            case Curve.parametric(curvature)  => Curve.parametric(math.max(-20, math.min(20, curvature + deltaCurve)))
            case other                        => other
          }

          val st      = if (selected) fadeState else TrackTool.NoFade
          val fdIn    = fv.fadeIn  // (don't remember this comment: "continue here. add delta")
          val fdInFr  = fdIn.numFrames + st.deltaFadeIn
          if (fdInFr > 0) {
            val fw    = framesToScreen(fdInFr).toFloat
            val fdC   = st.deltaFadeInCurve
            val shape = if (fdC != 0f) adjustFade(fdIn.curve, fdC) else fdIn.curve
            paintFade(shape, fw = fw, y1 = fdIn.floor, y2 = 1f, x = px, x0 = px)
          }
          val fdOut   = fv.fadeOut
          val fdOutFr = fdOut.numFrames + st.deltaFadeOut
          if (fdOutFr > 0) {
            val fw    = framesToScreen(fdOutFr).toFloat
            val fdC   = st.deltaFadeOutCurve
            val shape = if (fdC != 0f) adjustFade(fdOut.curve, fdC) else fdOut.curve
            val x0    = px + pw - 1
            paintFade(shape, fw = fw, y1 = 1f, y2 = fdOut.floor, x = x0 - fw, x0 = x0)
          }

        case _ =>
      }

      g.setClip(clipOrig)

      // --- label ---
      if (regionViewMode == RegionViewMode.TitledBox) {
        val isDark = if (colorOption.isEmpty) true else {
          g.translate(px, py)
          val colr0 = colorOption.get.rgba
          // XXX TODO -- a quick hack to mix with blue
          val colr  = if (selected) ((((colr0 & 0xFFFFFF) >> 1) & 0x7F7F7F) + 0x80) | (colr0 & 0xFF000000) else colr0
          val colr2 = new java.awt.Color(colr, true)
          g.setColor(colr2)
          g.fillRoundRect(1, 1, pw - 2, regionTitleHeight - 1, 4, 4)
          g.translate(-px, -py)
          // cf. https://stackoverflow.com/questions/596216/
          val b = ((colr & 0xFF0000) >> 16) * 30 + ((colr & 0xFF00) >> 8) * 59 + (colr & 0xFF) * 11
          b < 15000
        }

        g.clipRect(px + 2, py + 2, pw - 4, ph - 4)
        // possible unicodes: 2327 23DB 24DC 25C7 2715 29BB
        // val text  = if (view.muted) "\u23DB " + name else name
        val text: String = this match {
          case mv: TimelineObjView.HasMute if mv.muted => s"\u25C7 $name"
          case _ => name
        }
        val tx    = px + 4
        val ty    = py + regionTitleBaseline
        g.setPaint(if (isDark) pntNameShadowDark else pntNameShadowLight)
        g.drawString(text, tx, ty + 1)
        g.setPaint(if (isDark) pntNameDark else pntNameLight)
        g.drawString(text, tx, ty)
        //              stakeInfo(ar).foreach { info =>
        //                g2.setColor(awt.Color.yellow)
        //                g2.drawString(info, x + 4, y + hndlBaseline + hndlExtent)
        //              }
        g.setClip(clipOrig)
      }

      this match {
        case mv: TimelineObjView.HasMute if mv.muted =>
          g.setPaint(pntRegionBackgroundMuted)
          g.fillRoundRect(px, py, pw, ph, 5, 5)
        case _ =>
      }
    }
  }

  def paintFront(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit = ()
}