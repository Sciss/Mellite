/*
 *  ObjTimelineViewBasicImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.timeline

import de.sciss.audiowidgets.impl.TimelineNavigation
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.{Ident, IntObj, Obj, Source, SpanLikeObj, Txn => LTxn}
import de.sciss.mellite.impl.objview.ObjViewImpl
import de.sciss.mellite.{FadeViewMode, ObjTimelineView, ObjView, RegionViewMode, TimelineRendering, TimelineTool, TimelineView}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.Curve

import scala.math.{max, min}
import scala.swing.Graphics2D

trait ObjTimelineViewBasicImpl[T <: LTxn[T]] extends ObjTimelineView[T] with ObjViewImpl.Impl[T] {
  var trackIndex  : Int = _
  var trackHeight : Int = _
  // var nameOption  : Option[String] = _
  var spanValue   : SpanLike = _
  var spanH       : Source[T, SpanLikeObj[T]] = _

  protected var idH  : Source[T, Ident[T]] = _

  def span(implicit tx: T): SpanLikeObj[T]  = spanH()
  def id  (implicit tx: T): Ident[T]        = idH()

  def initAttrs(id: Ident[T], span: SpanLikeObj[T], obj: Obj[T])(implicit tx: T): this.type = {
    val attr      = obj.attr

    val trackIdxView = CellView.attr[T, Int, IntObj](attr, ObjTimelineView.attrTrackIndex)
    addDisposable(trackIdxView.react { implicit tx =>opt =>
      deferTx {
        trackIndex = opt.getOrElse(0)
      }
      fire(ObjView.Repaint(this))
    })
    trackIndex   = trackIdxView().getOrElse(0)

    val trackHView = CellView.attr[T, Int, IntObj](attr, ObjTimelineView.attrTrackHeight)
    addDisposable(trackHView.react { implicit tx =>opt =>
      deferTx {
        trackHeight = opt.getOrElse(TimelineView.DefaultTrackHeight)
      }
      fire(ObjView.Repaint(this))
    })
    trackHeight   = trackHView().getOrElse(TimelineView.DefaultTrackHeight)

    spanH         = tx.newHandle(span)
    spanValue     = span.value
    idH           = tx.newHandle(id)
    initAttrs(obj)
  }

  protected def paintInner(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering, selected: Boolean): Unit = ()

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

  def paintBack(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering): Unit = {
    val selected  = tlv.selectionModel.contains(this)
    val move0     = r.ttMoveState
    if (!move0.copy) {
      updateBounds (g, tlv, r, move0, selected = selected)
      paintBackImpl(g, tlv, r,        selected = selected)
    } else {
      // cheesy work around to show both original and copy
      val move1 = TimelineTool.NoMove
      updateBounds (g, tlv, r, move1, selected = selected)
      paintBackImpl(g, tlv, r,        selected = selected)
      updateBounds (g, tlv, r, move0, selected = selected)
      paintBackImpl(g, tlv, r,        selected = selected)
    }
  }

  private[this] def updateBounds(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering,
                                 moveState: TimelineTool.Move, selected: Boolean): Unit = {
    val canvas          = tlv.canvas
    var x1              = -5
    val peer            = canvas.canvasComponent.peer
    val w               = peer.getWidth
    var x2              = w + 5

    import canvas.{frameToScreen, modelPosToScreen}
    import r.{clipRect, ttResizeState => resizeState}

    def adjustStart(start: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime + resizeState.deltaStart
        if (dt0 >= 0) dt0 else {
          val minStart = TimelineNavigation.minStart(canvas.timelineModel)
          max(minStart - start, dt0)
        }
      } else 0L

    def adjustStop(stop: Long): Long =
      if (selected) {
        val dt0 = moveState.deltaTime + resizeState.deltaStop
        dt0
//        if (dt0 >= 0) dt0 else {
//          val minStart = TimelineNavigation.minStart(canvas.timelineModel)
//          dt0 // max(-(stop - total.start + TimelineView.MinDur), dt0)
//        }
      } else 0L

//    def adjustMove(start: Long): Long =
//      if (selected) {
//        val dt0 = moveState.deltaTime
//        if (dt0 >= 0) dt0 else {
//          val total = tlv.timelineModel.bounds
//          max(-(start - total.start), dt0)
//        }
//      } else 0L

    spanValue match {
      case Span(start, stop) =>
        val dStart    = adjustStart(start)
        val dStop     = adjustStop (stop )
        pStart        = start + dStart
        pStop         = stop  + dStop
        val newStop   = max(pStart + TimelineView.MinDur, pStop)
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

    val pTrkStart  = 
      if (selected) {
        max(0, trackIndex + moveState.deltaTrack + resizeState.deltaTrackStart)
      } else {
        trackIndex
      }
    val pTrkStop =
      if (selected) {
        pTrkStart + max(1, trackHeight + resizeState.deltaTrackStop - resizeState.deltaTrackStart)
      } else {
        pTrkStart + trackHeight
      }
      
    py        = modelPosToScreen(pTrkStart).toInt
    px        = x1
    pw        = x2 - x1
    ph        = modelPosToScreen(pTrkStop).toInt - py

    // clipped coordinates
    px1c      = max(px +  1, clipRect.x - 2)
    px2c      = min(px + pw, clipRect.x + clipRect.width + 3)
  }

  private[this] def paintBackImpl(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering, selected: Boolean): Unit = {
    val canvas          = tlv.canvas
    val timelineTools   = canvas.timelineTools
    val regionViewMode  = timelineTools.regionViewMode

    import canvas.framesToScreen
    import r.{ttFadeState => fadeState}

    if (px1c < px2c) {  // skip this if we are not overlapping with clip
      import r.{pntBackground, pntNameDark, pntNameLight, pntNameShadowDark, pntNameShadowLight, pntRegionBackground, pntRegionBackgroundMuted, pntRegionBackgroundSelected, pntRegionOutline, pntRegionOutlineSelected, regionTitleBaseline, regionTitleHeight}
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

      this match {
        case fv: ObjTimelineView.HasFade if timelineTools.fadeViewMode == FadeViewMode.Curve =>

          def adjustFade(in: Curve, deltaCurve: Float): Curve = in match {
            case Curve.linear                 => Curve.parametric(max(-20, min(20, deltaCurve)))
            case Curve.parametric(curvature)  => Curve.parametric(max(-20, min(20, curvature + deltaCurve)))
            case other                        => other
          }

          val st      = if (selected) fadeState else TimelineTool.NoFade
          val fdIn    = fv.fadeIn  // (don't remember this comment: "continue here. add delta")
          val fdInFr  = fdIn.numFrames + st.deltaFadeIn
          if (fdInFr > 0) {
            val fw    = framesToScreen(fdInFr).toFloat
            val fdC   = st.deltaFadeInCurve
            val shape = if (fdC != 0f) adjustFade(fdIn.curve, fdC) else fdIn.curve
            r.paintFade(g, shape, fw = fw, y1 = fdIn.floor, y2 = 1f, x = px, x0 = px, pyi = pyi, phi = phi)
          }
          val fdOut   = fv.fadeOut
          val fdOutFr = fdOut.numFrames + st.deltaFadeOut
          if (fdOutFr > 0) {
            val fw    = framesToScreen(fdOutFr).toFloat
            val fdC   = st.deltaFadeOutCurve
            val shape = if (fdC != 0f) adjustFade(fdOut.curve, fdC) else fdOut.curve
            val x0    = px + pw - 1
            r.paintFade(g, shape, fw = fw, y1 = 1f, y2 = fdOut.floor, x = x0 - fw, x0 = x0, pyi = pyi, phi = phi)
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
        // possible unicode values: 2327 23DB 24DC 25C7 2715 29BB
        // val text  = if (view.muted) "\u23DB " + name else name
        val text: String = this match {
          case mv: ObjTimelineView.HasMute if mv.muted => s"\u25C7 $name"
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
        case mv: ObjTimelineView.HasMute if mv.muted =>
          g.setPaint(pntRegionBackgroundMuted)
          g.fillRoundRect(px, py, pw, ph, 5, 5)
        case _ =>
      }
    }
  }

  def paintFront(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering): Unit = ()
}