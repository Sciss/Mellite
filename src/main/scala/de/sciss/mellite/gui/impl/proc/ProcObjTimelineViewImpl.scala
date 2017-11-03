/*
 *  ProcObjTimelineViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui.impl.proc

import de.sciss.file._
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, TxnLike}
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget
import de.sciss.mellite.gui.impl.timeline.TimelineObjViewImpl
import de.sciss.mellite.gui.{AttrCellView, ObjView, SonogramManager, TimelineObjView, TimelineRendering, TimelineView}
import de.sciss.sonogram.{Overview => SonoOverview}
import de.sciss.span.Span
import de.sciss.synth.proc
import de.sciss.synth.proc.{AudioCue, Proc, TimeRef}

import scala.concurrent.stm.{Ref, TSet}
import scala.swing.Graphics2D
import scala.util.control.NonFatal

final class ProcObjTimelineViewImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Proc[S]],
                                                 var busOption : Option[Int], val context: TimelineObjView.Context[S])
  extends ProcObjViewImpl[S]
    with TimelineObjViewImpl.HasGainImpl[S]
    with TimelineObjViewImpl.HasMuteImpl[S]
    with TimelineObjViewImpl.HasFadeImpl[S]
    with ProcObjView.Timeline[S] { self =>

  override def toString = s"ProcView($name, $spanValue, $audio)"

  private[this] var audio         = Option.empty[AudioCue]
  private[this] var failedAcquire = false
  private[this] var sonogram      = Option.empty[SonoOverview]
  private[this] val _targets      = TSet.empty[LinkTarget[S]]

  def addTarget   (tgt: LinkTarget[S])(implicit tx: TxnLike): Unit = _targets.add   (tgt)(tx.peer)
  def removeTarget(tgt: LinkTarget[S])(implicit tx: TxnLike): Unit = _targets.remove(tgt)(tx.peer)

  def targets(implicit tx: TxnLike): Set[LinkTarget[S]] = {
    import TxnLike.peer
    _targets.toSet
  }

  def debugString: String = {
    val basic1S = s"span = $spanValue, trackIndex = $trackIndex, nameOption = $nameOption"
    val basic2S = s"muted = $muted, audio = $audio"
    val basic3S = s"fadeIn = $fadeIn, fadeOut = $fadeOut, gain = $gain, busOption = $busOption"
    val procS   = s"ProcView($basic1S, $basic2S, $basic3S)"
    // val inputS   = inputs.mkString("  inputs  = [", ", ", "]\n")
    // val outputS  = outputs.mkString("  outputs = [", ", ", "]\n")
    // s"$procC\n$inputS$outputS"
    procS
  }

  def fireRepaint()(implicit tx: S#Tx): Unit = fire(ObjView.Repaint(this))

  def init(id: S#ID, span: SpanLikeObj[S], obj: Proc[S])(implicit tx: S#Tx): this.type = {
    initAttrs(id, span, obj)

    val attr    = obj.attr
    val cueView = AttrCellView[S, AudioCue, AudioCue.Obj](attr, Proc.graphAudio)
    disposables ::= cueView.react { implicit tx => newAudio =>
      deferAndRepaint {
        val newSonogram = audio.map(_.artifact) != newAudio.map(_.artifact)
        audio = newAudio
        if (newSonogram) releaseSonogram()
      }
    }
    audio = cueView()

    // attr.iterator.foreach { case (key, value) => addAttr(key, value) }
    import Proc.mainIn
    attr.get(mainIn).foreach(v => addAttrIn(key = mainIn, value = v, fire = false))
    disposables ::= attr.changed.react { implicit tx => upd => upd.changes.foreach {
      case Obj.AttrAdded   (`mainIn`, value) => addAttrIn   (key = mainIn, value = value)
      case Obj.AttrRemoved (`mainIn`, value) => removeAttrIn(value)
      case Obj.AttrReplaced(`mainIn`, before, now) =>
        removeAttrIn(before)
        addAttrIn(key = mainIn, value = now)
      case _ =>
    }}

    obj.outputs.iterator.foreach(outputAdded)

    disposables ::= obj.changed.react { implicit tx => upd =>
      upd.changes.foreach {
        case proc.Proc.OutputAdded  (out) => outputAdded  (out)
        case proc.Proc.OutputRemoved(out) => outputRemoved(out)
        case _ =>
      }
    }

    this
  }

  private[this] def outputAdded(out: proc.Output[S])(implicit tx: S#Tx): Unit =
    context.putAux[ProcObjView.Timeline[S]](out.id, this)

  private[this] def outputRemoved(out: proc.Output[S])(implicit tx: S#Tx): Unit =
    context.removeAux(out.id)

  private[this] val attrInRef = Ref(Option.empty[InputAttrImpl[S]])
  private[this] var attrInEDT =     Option.empty[InputAttrImpl[S]]

  private[this] def removeAttrIn(value: Obj[S])(implicit tx: S#Tx): Unit = {
    import TxnLike.peer
    attrInRef.swap(None).foreach { view =>
      view.dispose()
      deferAndRepaint {
        attrInEDT = None
      }
    }
  }

  private[this] def addAttrIn(key: String, value: Obj[S], fire: Boolean = true)(implicit tx: S#Tx): Unit = {
    import TxnLike.peer
    val viewOpt: Option[InputAttrImpl[S]] = value match {
      case tl: proc.Timeline[S] =>
        val tlView  = new InputAttrTimeline(this, key, tl, tx)
        Some(tlView)

      case _: proc.Grapheme[S] =>
        println("addAttrIn: Grapheme")
        ???!

      case f: proc.Folder[S] =>
        val tlView  = new InputAttrFolder(this, key, f, tx)
        Some(tlView)

      case out: proc.Output[S] =>
        val tlView  = new InputAttrOutput(this, key, out, tx)
        Some(tlView)

      case _ => None
    }

    val old = attrInRef.swap(viewOpt)
    old.foreach(_.dispose())
    import de.sciss.equal.Implicits._
    if (viewOpt !== old) {
      deferTx {
        attrInEDT = viewOpt
      }
      if (fire) this.fire(ObjView.Repaint(this))
    }
  }

  override def paintFront(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit =
    if (pStart > Long.MinValue) attrInEDT.foreach { attrInView =>
      attrInView.paintInputAttr(g, tlv = tlv, r = r, px1c = px1c, px2c = px2c)
    }

  // paint sonogram
  override protected def paintInner(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering,
                                    selected: Boolean): Unit =
    audio.foreach { audioVal =>
      val sonogramOpt = sonogram.orElse(acquireSonogram())

      sonogramOpt.foreach { sonogram =>
        val srRatio     = sonogram.inputSpec.sampleRate / TimeRef.SampleRate
        // dStart is the frame inside the audio-file corresponding
        // to the region's left margin. That is, if the grapheme segment
        // starts early than the region (its start is less than zero),
        // the frame accordingly increases.
        val dStart      = (audioVal.offset /* - segm.span.start */ +
          (if (selected) r.ttResizeState.deltaStart else 0L)) * srRatio
        // a factor to convert from pixel space to audio-file frames
        val canvas      = tlv.canvas
        val s2f         = canvas.screenToFrames(1) * srRatio
        val lenC        = (px2c - px1c) * s2f
        val visualBoost = canvas.trackTools.visualBoost
        val boost       = if (selected) r.ttGainState.factor * visualBoost else visualBoost
        r.sonogramBoost = (audioVal.gain * gain).toFloat * boost
        val startP      = (px1c - px) * s2f + dStart
        val stopP       = startP + lenC
        val w1          = px2c - px1c
        // println(s"${pv.name}; audio.offset = ${audio.offset}, segm.span.start = ${segm.span.start}, dStart = $dStart, px1C = $px1C, startC = $startC, startP = $startP")
        // println(f"spanStart = $startP%1.2f, spanStop = $stopP%1.2f, tx = $px1c, ty = $pyi, width = $w1, height = $phi, boost = ${r.sonogramBoost}%1.2f")

        sonogram.paint(spanStart = startP, spanStop = stopP, g2 = g,
          tx = px1c, ty = pyi, width = w1, height = phi, ctrl = r)
      }
    }

  private[this] def releaseSonogram(): Unit =
    sonogram.foreach { ovr =>
      sonogram = None
      SonogramManager.release(ovr)
    }

  override def name: String = nameOption.getOrElse {
    audio.fold(TimelineObjView.Unnamed)(_./* value. */artifact.base)
  }

  private[this] def acquireSonogram(): Option[SonoOverview] = {
    if (failedAcquire) return None
    releaseSonogram()
    sonogram = audio.flatMap { audioVal =>
      try {
        val ovr = SonogramManager.acquire(audioVal./* value. */artifact)  // XXX TODO: remove `Try` once manager is fixed
        failedAcquire = false
        Some(ovr)
      } catch {
        case NonFatal(_) =>
          failedAcquire = true
          None
      }
    }
    sonogram
  }

  override def dispose()(implicit tx: S#Tx): Unit = {
    super.dispose()
    import TxnLike.peer
    val proc = obj
    proc.outputs.iterator.foreach(outputRemoved)
    attrInRef.swap(None).foreach(_.dispose())
    deferTx(disposeGUI())
  }

  private[this] def disposeGUI(): Unit = releaseSonogram()

  def isGlobal: Boolean = {
    import de.sciss.equal.Implicits._
    spanValue === Span.All
  }
}
