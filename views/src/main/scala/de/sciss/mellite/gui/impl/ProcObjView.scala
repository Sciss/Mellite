/*
 *  ProcObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl

import de.sciss.desktop
import de.sciss.desktop.OptionPane
import de.sciss.file._
import de.sciss.fingertree.RangedSeq
import de.sciss.icons.raphael
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, IdentifierMap, Obj, TxnLike}
import de.sciss.lucre.swing.{Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.timeline.TimelineObjViewImpl
import de.sciss.sonogram.{Overview => SonoOverview}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{AudioCue, AuxContext, ObjKeys, Proc, TimeRef}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.{Ref, TSet}
import scala.language.implicitConversions
import scala.swing.Graphics2D
import scala.util.control.NonFatal

object ProcObjView extends ListObjView.Factory with TimelineObjView.Factory {
  type E[~ <: stm.Sys[~]] = Proc[~]

  val icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Cogs)
  val prefix    = "Proc"
  val humanName = "Process"
  def tpe       = Proc
  def category  = ObjView.categComposition

  def hasMakeDialog = true

  def mkListView[S <: Sys[S]](obj: Proc[S])(implicit tx: S#Tx): ProcObjView[S] with ListObjView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  type Config[S <: stm.Sys[S]] = String

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (implicit cursor: stm.Cursor[S]): Option[Config[S]] = {
    val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
      messageType = OptionPane.Message.Question, initial = prefix)
    opt.title = s"New $prefix"
    val res = opt.show(window)
    res
  }

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Proc[S]
    obj.name = name
    obj :: Nil
  }

  type LinkMap[S <: stm.Sys[S]] = Map[String, Vec[ProcObjView.Link[S]]]
  type ProcMap[S <: stm.Sys[S]] = IdentifierMap[S#ID, S#Tx, ProcObjView[S]]
  type ScanMap[S <: stm.Sys[S]] = IdentifierMap[S#ID, S#Tx, (String, stm.Source[S#Tx, S#ID])]

  type SelectionModel[S <: Sys[S]] = gui.SelectionModel[S, ProcObjView[S]]

  /** Constructs a new proc view from a given proc, and a map with the known proc (views).
    * This will automatically add the new view to the map!
    */
  def mkTimelineView[S <: Sys[S]](timedID: S#ID, span: SpanLikeObj[S], obj: Proc[S],
                                  context: TimelineObjView.Context[S])(implicit tx: S#Tx): ProcObjView.Timeline[S] = {
    val attr = obj.attr
    val bus  = attr.$[IntObj](ObjKeys.attrBus    ).map(_.value)
    new TimelineImpl[S](tx.newHandle(obj), busOption = bus, context = context)
      .init(timedID, span, obj)
  }

  // -------- Proc --------

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Proc[S]])
    extends Impl[S]

  private trait Impl[S <: Sys[S]]
    extends ListObjView[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.EmptyRenderer[S]
    with ListObjViewImpl.NonEditable[S]
    with ProcObjView[S] {

    override def obj(implicit tx: S#Tx): Proc[S] = objH()

    final def factory = ProcObjView

    final def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    final def openView(parent: Option[Window[S]])
                      (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = CodeFrame.proc(obj)
      Some(frame)
    }
  }

  private trait InputAttr[S <: Sys[S]] extends Disposable[S#Tx] {
    def paintInputAttr(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering, px1c: Int, px2c: Int): Unit
  }

  private final class InputAttrTimeline[S <: Sys[S]](parent: ProcObjView.Timeline[S],
                                                     tl: proc.Timeline[S], tx0: S#Tx)
    extends InputAttr[S] {

    // source views are updated by calling `copy` as they appear and disappear
    private final class Elem(val span: SpanLike, val source: Option[ProcObjView.Timeline[S]],
                             obs: Disposable[S#Tx]) extends Disposable[S#Tx] {
      def point: (Long, Long) = TimelineObjView.spanToPoint(span)

      def dispose()(implicit tx: S#Tx): Unit = obs.dispose()

      def copy(newSource: Option[ProcObjView.Timeline[S]]): Elem =
        new Elem(span = span, source = newSource, obs = obs)
    }

    private[this] val viewMap   = tx0.newInMemoryIDMap[Elem]
    private[this] val viewSet   = TSet.empty[Elem]  // because `viewMap.iterator` does not exist...

    // EDT
    private[this] var rangeSeq  = RangedSeq.empty[Elem, Long](_.point, Ordering.Long)

    private[this] val observer: Disposable[S#Tx] =
      tl.changed.react { implicit tx => upd => upd.changes.foreach {
        case proc.Timeline.Added  (span  , entry) => addAttrIn   (span, entry, fire = true)
        case proc.Timeline.Removed(span  , entry) => removeAttrIn(span, entry)
        case proc.Timeline.Moved  (spanCh, entry) =>
          removeAttrIn(spanCh.before, entry)
          addAttrIn   (spanCh.now   , entry, fire = true)
      }} (tx0)

    // init
    tl.iterator(tx0).foreach { case (span, xs) =>
      xs.foreach(addAttrIn(span, _, fire = false)(tx0))
    }

    def paintInputAttr(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering, px1c: Int, px2c: Int): Unit = {
      // println(s"paintInputAttr(${rangeSeq.iterator.size})")
      val canvas  = tlv.canvas
      val pStart  = parent.pStart
      val pStop   = parent.pStop
      val py      = parent.py
      val start   = math.max(pStart, canvas.screenToFrame(px1c - 4).toLong) - pStart
      val stop    = math.min(pStop , canvas.screenToFrame(px2c + 4).toLong) - pStart

      val it      = rangeSeq.filterOverlaps((start, stop))
      if (it.isEmpty) return

      /*
        Algorithm: (patching-study.svg)

        - foreground and background shapes
        - foreground is one of: <start>, <stop>, <stop-cut>
          each of which occupies a horizontal space.
          A <start> overlapping a <stop> transforms it into
          a <stop-cut>
        - background: a set of lines. foreground shapes
          but these lines
        - traverse range-seq, build up foreground and background
          and perform cuts as we proceed
        - optional extension: add <plus> decorator to <start>
          if a line exists that extends beyond the start point
        - draw background shapes with dashed stroke
        - fill foreground shapes

        Data structure:

         foreground: [x: Int, tpe: Int] * N

           where tpe = 0 start, 1 start with plus, 2 stop, 3 stop-cut
           and predefined 'padding' for each shape

           for start, we add (source-pos << 2) or (0x1fffffff << 2)

         background: [x-start: Int, x-stop: Int] * N

       */

      // ---- calculate shapes ----

      var fgSize = 0
      var bgSize = 0
      var fg     = new Array[Int](16 * 2) // XXX TODO -- avoid allocation
      var bg     = new Array[Int](16 * 2)

      it.foreach { elem =>
        @inline
        def frameToScreen(pos: Long): Int = canvas.frameToScreen(pos + pStart).toInt

        def addToFg(x: Int, tpe: Int): Unit = {
          if (fgSize == fg.length) {
            val tmp = fg
            fg = new Array[Int](fgSize * 2)
            System.arraycopy(tmp, 0, fg, 0, fgSize)
          }
          fg(fgSize)      = x
          fg(fgSize + 1)  = tpe
          fgSize += 2
        }

        def addToBg(x1: Int, x2: Int): Unit = {
          if (bgSize == bg.length) {
            val tmp = bg
            bg = new Array[Int](bgSize * 2)
            System.arraycopy(tmp, 0, bg, 0, bgSize)
          }
          bg(bgSize)      = x1
          bg(bgSize + 1)  = x2
          bgSize += 2
        }

        def addStart(pos: Long, elem: Elem): Unit = {
          val x   = frameToScreen(pos)
          val tpe = elem.source.fold(0x7ffffffc)(src => (src.py + src.ph) << 2)

          addToFg(x, tpe)

          val x1  = x - 3 // stop overlaps
          val x2  = x + 3 // stop overlaps

          var i = 0
          while (i < fgSize) {
            if (fg(i + 1) == 2 /* stop */) {
              val xStop = fg(i)
              if (xStop > x1 && xStop < x2) {
                fg(i)     = x1
                fg(i + 1) = 3 // stop-cut
              }
            }
            i += 2
          }
        }

        def addStop(pos: Long): Unit = {
          val x = frameToScreen(pos)
          addToFg(x, 2)
        }

        def addSpan(start: Long, stop: Long): Unit = {
          val thisStart = frameToScreen(start)
          val thisStop  = frameToScreen(stop )

          // 'union'
          var i = 0
          var startFound = false
          while (i < bgSize && !startFound) {
            val thatStop = bg(i + 1)
            if (thisStart <= thatStop) {
              startFound = true
            } else {
              i += 2
            }
          }
          
          if (i < bgSize) {
            bg(i) = math.min(bg(i), thisStart)
            var j = i + 2
            var stopFound = false
            while (j < bgSize && !stopFound) {
              val thatStart = bg(j)
              if (thisStop < thatStart) {
                stopFound = true
              } else {
                j += 2
              }
            }
            j -= 2
            if (j > i) {  // 'compress' the array, remove consumed elements
              System.arraycopy(bg, j + 1, bg, i + 1, bgSize - (j + 1))
              bgSize -= j - i
              j = i
            }
            bg(j + 1) = math.max(bg(j + 1), thisStop)

          } else {
            addToBg(thisStart, thisStop)
          }
        }

        elem.span match {
          case Span(eStart, eStop) =>
            addStart(eStart, elem)
            addStop (eStop )
            addSpan (eStart, eStop)
          case Span.From(eStart) =>
            addStart(eStart, elem)
            // addSpan(eStart, ?)  XXX TODO
          case Span.Until(eStop) =>
            addStop(eStop)
            addSpan (0L, eStop)
          case _ =>
        }
      }

      // XXX TODO: subtract foreground from background

      // ---- draw ----

      def drawArrow(x: Int, srcY: Int): Unit = {
        import r.shape1
        shape1.reset()
        shape1.moveTo(x + 0.5, py)
        shape1.lineTo(x - 2.5, py - 6)
        shape1.lineTo(x + 3.5, py - 6)
        shape1.closePath()
        g.fill(shape1)

        if (srcY != 0x1fffffff) {
          g.drawLine(x, py - 6, x, srcY /* source.py + source.ph */)
        }
      }

      @inline
      def drawStop(x: Int): Unit =
        g.drawLine(x, py, x, py - 6)

      @inline
      def drawStopCut(x: Int): Unit =
        g.drawLine(x + 1, py, x - 2, py - 6)

      // ---- draw foreground ----

      g.setPaint(r.pntInlet)
      var ii = 0
      while (ii < fgSize) {
        val x   = fg(ii)
        val tpe = fg(ii + 1)
        (tpe & 3: @switch) match {
          case 0 => drawArrow  (x, tpe >> 2)
          case 1 => drawArrow  (x, tpe >> 2) // XXX TODO: drawPlus
          case 2 => drawStop   (x)
          case 3 => drawStopCut(x)
        }
        ii += 2
      }

      // ---- draw background ----

      val strkOrig = g.getStroke
      g.setPaint(r.pntInletSpan)
      g.setStroke(r.strokeInletSpan)
      var jj = 0
      while (jj < bgSize) {
        val x1 = bg(jj)
        val x2 = bg(jj + 1)
        g.drawLine(x1, py - 3, x2, py - 3)
        jj += 2
      }
      g.setStroke(strkOrig)
    }

    private def addAttrIn(span: SpanLike, entry: proc.Timeline.Timed[S], fire: Boolean)(implicit tx: S#Tx): Unit =
      entry.value match {
        case out: proc.Output[S] =>
          import TxnLike.peer
          val idH      = tx.newHandle(entry.id)
          val viewInit = parent.context.getAux[ProcObjView.Timeline[S]](out.id)
          val obs  = parent.context.observeAux[ProcObjView.Timeline[S]](out.id) { implicit tx => upd =>
            val id = idH()
            viewMap.get(id).foreach { elem1 =>
              // elem2 keeps the observer, so no `dispose` call here
              val elem2 = upd match {
                case AuxContext.Added(_, sourceView)  => elem1.copy(Some(sourceView))
                case AuxContext.Removed(_)            => elem1.copy(None)
              }
              viewMap.put(id, elem2)  // replace
              viewSet.remove(elem1)
              viewSet.add   (elem2)
              deferTx {
                rangeSeq -= elem1
                rangeSeq += elem2
              }
              parent.fireRepaint()
            }
          }
          val elem0 = new Elem(span, viewInit, obs)
          viewMap.put(entry.id, elem0)
          viewSet.add(elem0)
          deferTx {
            rangeSeq += elem0
          }
          if (fire) parent.fireRepaint()

        case _ => // no others supported ATM
      }

    private def removeAttrIn(span: SpanLike, entry: proc.Timeline.Timed[S])(implicit tx: S#Tx): Unit =
      viewMap.get(entry.id).foreach { elem0 =>
        import TxnLike.peer
        viewMap.remove(entry.id)
        viewSet.remove(elem0)
        deferTx {
          rangeSeq -= elem0
        }
        elem0.dispose()
        parent.fireRepaint()
      }

    def dispose()(implicit tx: S#Tx): Unit = {
      import TxnLike.peer
      observer.dispose()
      viewSet.foreach(_.dispose())
      viewSet.clear()
    }
  }

  private final class TimelineImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Proc[S]],
                                                var busOption : Option[Int], val context: TimelineObjView.Context[S])
    extends Impl[S]
    with TimelineObjViewImpl.HasGainImpl[S]
    with TimelineObjViewImpl.HasMuteImpl[S]
    with TimelineObjViewImpl.HasFadeImpl[S]
    with ProcObjView.Timeline[S] { self =>

    override def toString = s"ProcView($name, $spanValue, $audio)"

    private[this] var audio         = Option.empty[AudioCue]
    private[this] var failedAcquire = false
    private[this] var sonogram      = Option.empty[SonoOverview]

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

      // XXX TODO -- should use a dynamic AttrCellView here
      val attr = obj.attr
      attr.$[AudioCue.Obj](Proc.graphAudio).foreach { audio0 =>
        disposables ::= audio0.changed.react { implicit tx => upd =>
          val newAudio = upd.now // calcAudio(upd.grapheme)
          deferAndRepaint {
            val newSonogram = upd.before.artifact != upd.now.artifact
            audio = Some(newAudio)
            if (newSonogram) releaseSonogram()
          }
        }
        audio = Some(audio0.value) // calcAudio(g)
      }

      // attr.iterator.foreach { case (key, value) => addAttr(key, value) }
      import Proc.mainIn
      attr.get(mainIn).foreach(addAttrIn(_, fire = false))
      disposables ::= attr.changed.react { implicit tx => upd => upd.changes.foreach {
        case Obj.AttrAdded   (`mainIn`, value) => addAttrIn(value, fire = true)
        case Obj.AttrRemoved (`mainIn`, value) => ???!
        case Obj.AttrReplaced(`mainIn`, before, now) => ???!
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

    private[this] val attrInRef = Ref(Option.empty[InputAttr[S]])
    private[this] var attrInEDT =     Option.empty[InputAttr[S]]

    private[this] def addAttrIn(value: Obj[S], fire: Boolean)(implicit tx: S#Tx): Unit = value match {
      case tl: proc.Timeline[S] =>
        import TxnLike.peer
        // println("addAttrIn: Timeline")
        val tlView  = new InputAttrTimeline(this, tl, tx)
        val opt     =  Some(tlView)
        attrInRef.swap(opt).foreach(_.dispose())
        deferAndRepaint {
          attrInEDT = opt
        }

      case gr: proc.Grapheme[S] =>
        println("addAttrIn: Grapheme")
        ???!
      case f: proc.Folder  [S] =>
        println("addAttrIn: Folder")
        ???!
      case out: proc.Output  [S] =>
        println("addAttrIn: Output")
        ???!
      case _ =>
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
          val dStart      = (audioVal.fileOffset /* offset - segm.span.start */ +
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

    override def name = nameOption.getOrElse {
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

  final case class Link[S <: stm.Sys[S]](target: ProcObjView.Timeline[S], targetKey: String)

  /** A data set for graphical display of a proc. Accessors and mutators should
    * only be called on the event dispatch thread. Mutators are plain variables
    * and do not affect the underlying model. They should typically only be called
    * in response to observing a change in the model.
    */
  trait Timeline[S <: stm.Sys[S]]
    extends ProcObjView[S] with TimelineObjView[S]
    with TimelineObjView.HasMute
    with TimelineObjView.HasGain
    with TimelineObjView.HasFade {

    /** Convenience check for `span == Span.All` */
    def isGlobal: Boolean

    def context: TimelineObjView.Context[S]

    def fireRepaint()(implicit tx: S#Tx): Unit

    var busOption: Option[Int]

    def debugString: String

    def px: Int
    def py: Int
    def pw: Int
    def ph: Int

    def pStart: Long
    def pStop : Long
  }
}
trait ProcObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Proc[S]

  def objH: stm.Source[S#Tx, Proc[S]]
}