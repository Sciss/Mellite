/*
 *  ProcObjTimelineViewImpl.scala
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

import java.awt.RenderingHints

import de.sciss.asyncfile.Ops.URIOps
import de.sciss.file._
import de.sciss.lucre.{Folder, Ident, Obj, Source, SpanLikeObj, TxnLike}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.Txn.peer
import de.sciss.mellite.Mellite.???!
import de.sciss.mellite.impl.ObjTimelineViewImpl
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import de.sciss.mellite.{ObjTimelineView, ObjView, SonogramManager, TimelineRendering, TimelineView}
import de.sciss.sonogram.{Overview => SonoOverview}
import de.sciss.span.Span
import de.sciss.proc
import de.sciss.proc.{AudioCue, Proc, TimeRef}

import scala.concurrent.stm.{Ref, TSet}
import scala.swing.Graphics2D
import scala.util.control.NonFatal

final class ProcObjTimelineViewImpl[T <: Txn[T]](val objH: Source[T, Proc[T]],
                                                 var busOption : Option[Int], val context: ObjTimelineView.Context[T])
  extends ProcObjViewImpl[T]
    with ObjTimelineViewImpl.HasGainImpl[T]
    with ObjTimelineViewImpl.HasMuteImpl[T]
    with ObjTimelineViewImpl.HasFadeImpl[T]
    with ProcObjView.Timeline[T] { self =>

  override def toString = s"ProcView($name, $spanValue, $audio)"

  private[this] var audio         = Option.empty[AudioCue]
  private[this] var failedAcquire = false
  private[this] var sonogram      = Option.empty[SonoOverview]
  private[this] val _targets      = TSet.empty[LinkTarget[T]]

  def addTarget   (tgt: LinkTarget[T])(implicit tx: TxnLike): Unit = _targets.add   (tgt)(tx.peer)
  def removeTarget(tgt: LinkTarget[T])(implicit tx: TxnLike): Unit = _targets.remove(tgt)(tx.peer)

  def targets(implicit tx: TxnLike): Set[LinkTarget[T]] = {
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

  def fireRepaint()(implicit tx: T): Unit = fire(ObjView.Repaint(this))

  def init(id: Ident[T], span: SpanLikeObj[T], obj: Proc[T])(implicit tx: T): this.type = {
    initAttrs(id, span, obj)

    val attr    = obj.attr
    val cueView = CellView.attr[T, AudioCue, AudioCue.Obj](attr, Proc.graphAudio)
    addDisposable(cueView.react { implicit tx =>newAudio =>
      deferAndRepaint {
        val newSonogram = audio.map(_.artifact) != newAudio.map(_.artifact)
        audio = newAudio
        if (newSonogram) releaseSonogram()
      }
    })
    audio = cueView()

    // attr.iterator.foreach { case (key, value) => addAttr(key, value) }
    import Proc.mainIn
    attr.get(mainIn).foreach(v => addAttrIn(key = mainIn, value = v, fire = false))
    addDisposable(attr.changed.react { implicit tx =>upd => upd.changes.foreach {
      case Obj.AttrAdded   (`mainIn`, value) => addAttrIn   (key = mainIn, value = value)
      case Obj.AttrRemoved (`mainIn`, value) => removeAttrIn(value)
      case Obj.AttrReplaced(`mainIn`, before, now) =>
        removeAttrIn(before)
        addAttrIn(key = mainIn, value = now)
      case _ =>
    }})

    obj.outputs.iterator.foreach(outputAdded)

    addDisposable(obj.changed.react { implicit tx =>upd =>
      upd.changes.foreach {
        case proc.Proc.OutputAdded  (out) => outputAdded  (out)
        case proc.Proc.OutputRemoved(out) => outputRemoved(out)
        case _ =>
      }
    })

    this
  }

  private[this] def outputAdded(out: Proc.Output[T])(implicit tx: T): Unit =
    context.putAux[ProcObjView.Timeline[T]](out.id, this)

  private[this] def outputRemoved(out: Proc.Output[T])(implicit tx: T): Unit =
    context.removeAux(out.id)

  private[this] val attrInRef = Ref(Option.empty[InputAttrImpl[T]])
  private[this] var attrInEDT =     Option.empty[InputAttrImpl[T]]

  private[this] def removeAttrIn(value: Obj[T])(implicit tx: T): Unit = {
    attrInRef.swap(None).foreach { view =>
      view.dispose()
      deferAndRepaint {
        attrInEDT = None
      }
    }
  }

  private[this] def addAttrIn(key: String, value: Obj[T], fire: Boolean = true)(implicit tx: T): Unit = {
    val viewOpt: Option[InputAttrImpl[T]] = value match {
      case tl: proc.Timeline[T] =>
        val tlView  = new InputAttrTimeline(this, key, tl, tx)
        Some(tlView)

      case _: proc.Grapheme[T] =>
        println("addAttrIn: Grapheme")
        ???!

      case f: Folder[T] =>
        val tlView  = new InputAttrFolder(this, key, f, tx)
        Some(tlView)

      case out: Proc.Output[T] =>
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

  override def paintFront(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering): Unit =
    if (pStart > Long.MinValue) attrInEDT.foreach { attrInView =>
      attrInView.paintInputAttr(g, tlv = tlv, r = r, px1c = px1c, px2c = px2c)
    }

  // paint sonogram
  override protected def paintInner(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering,
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
        val visualBoost = canvas.timelineTools.visualBoost
        val boost       = if (selected) r.ttGainState.factor * visualBoost else visualBoost
        r.sonogramBoost = (audioVal.gain * gain).toFloat * boost
        val startP      = (px1c - px) * s2f + dStart
        val stopP       = startP + lenC
        val w1          = px2c - px1c
        // println(s"${pv.name}; audio.offset = ${audio.offset}, segm.span.start = ${segm.span.start}, dStart = $dStart, px1C = $px1C, startC = $startC, startP = $startP")
        // println(f"spanStart = $startP%1.2f, spanStop = $stopP%1.2f, tx = $px1c, ty = $pyi, width = $w1, height = $phi, boost = ${r.sonogramBoost}%1.2f")

        val hintOld0  = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION)
        val hintOld   = if (hintOld0 == null) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR else hintOld0
        try {
          g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          sonogram.paint(spanStart = startP, spanStop = stopP, g2 = g,
            tx = px1c, ty = pyi, width = w1, height = phi, ctrl = r)
        } catch {
          case NonFatal(_) => // XXX TODO
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hintOld)
      }
    }

  private[this] def releaseSonogram(): Unit =
    sonogram.foreach { ovr =>
      sonogram = None
      SonogramManager.release(ovr)
    }

  override def name: String = nameOption.getOrElse {
    audio.fold(ObjView.Unnamed)(_./* value. */artifact.base)
  }

  private[this] def acquireSonogram(): Option[SonoOverview] = {
    if (failedAcquire) return None
    releaseSonogram()
    sonogram = audio.flatMap { audioVal =>
      try {
        val f   = new File(audioVal.artifact)
        val ovr = SonogramManager.acquire(f)
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

  override def dispose()(implicit tx: T): Unit = {
    super.dispose()
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
