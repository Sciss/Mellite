/*
 *  ProcObjView.scala
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
package gui
package impl

import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.desktop
import de.sciss.desktop.OptionPane
import de.sciss.file._
import de.sciss.fingertree.RangedSeq
import de.sciss.icons.raphael
import de.sciss.lucre.expr.{IntObj, SpanLikeObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Identifiable, IdentifierMap, Obj, TxnLike}
import de.sciss.lucre.swing.{Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.{EditAttrMap, EditFolderRemoveObj, EditTimelineRemoveObj}
import de.sciss.mellite.gui.impl.timeline.TimelineObjViewImpl
import de.sciss.sonogram.{Overview => SonoOverview}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{AudioCue, AuxContext, ObjKeys, Proc, TimeRef, Workspace}

import scala.annotation.switch
import scala.concurrent.stm.{Ref, TSet}
import scala.swing.Graphics2D
import scala.util.control.NonFatal

object ProcObjView extends ListObjView.Factory with TimelineObjView.Factory {
  type E[~ <: stm.Sys[~]] = Proc[~]

  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Cogs)
  val prefix        : String    = "Proc"
  val humanName     : String    = "Process"
  def tpe           : Obj.Type  = Proc
  def category      : String    = ObjView.categComposition
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Proc[S])(implicit tx: S#Tx): ProcObjView[S] with ListObjView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  type Config[S <: stm.Sys[S]] = String

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = {
    val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
      messageType = OptionPane.Message.Question, initial = prefix)
    opt.title = s"New $prefix"
    val res = opt.show(window)
    res.foreach(ok)
  }

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Proc[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

//  type LinkMap[S <: stm.Sys[S]] = Map[String, Vec[ProcObjView.Link[S]]]
//  type ProcMap[S <: stm.Sys[S]] = IdentifierMap[S#ID, S#Tx, ProcObjView[S]]
//  type ScanMap[S <: stm.Sys[S]] = IdentifierMap[S#ID, S#Tx, (String, stm.Source[S#Tx, S#ID])]

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

    final def factory   : ObjView.Factory = ProcObjView
    final def isViewable: Boolean         = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    final def openView(parent: Option[Window[S]])
                      (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = CodeFrame.proc(obj)
      Some(frame)
    }
  }

  trait LinkTarget[S <: stm.Sys[S]] {
    def attr: InputAttr[S]
    def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]
  }

  private final class LinkTargetTimeline[S <: Sys[S]](val attr: InputAttrTimeline[S],
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

  private final class LinkTargetOutput[S <: Sys[S]](val attr: InputAttrOutput[S])
    extends LinkTarget[S] {

    override def toString: String = s"LinkTargetOutput($attr)@${hashCode.toHexString}"

    def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
      // val out = attr.output
      val obj  = attr.parent.obj
      val edit = EditAttrMap.remove("Output", obj = obj, key = attr.key)
      Some(edit)
    }
  }

  private final class LinkTargetFolder[S <: Sys[S]](val attr: InputAttrFolder[S],
                                                    objH : stm.Source[S#Tx, Obj[S]])
    extends LinkTarget[S] {

    override def toString: String = s"LinkTargetFolder($attr)@${hashCode.toHexString}"

    def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
      val f     = attr.folder
      val obj   = objH()
      val index = f.indexOf(obj)
      if (index < 0) None else {
        val edit = EditFolderRemoveObj("Proc", f, index = index, child = obj)
        Some(edit)
      }
    }
  }

  /* Reference to an element referred to by an input-attr.
   * Source views are updated by calling `copy` as they appear and disappear
   */
  private final class InputElem[S <: stm.Sys[S]](val span: SpanLike, val source: Option[ProcObjView.Timeline[S]],
                                             val target: LinkTarget[S], obs: Disposable[S#Tx], tx0: S#Tx)
    extends Disposable[S#Tx] {

    source.foreach(_.addTarget(target)(tx0))

    def point: (Long, Long) = TimelineObjView.spanToPoint(span)

    def dispose()(implicit tx: S#Tx): Unit = {
      obs.dispose()
      source.foreach(_.removeTarget(target))
    }

    def copy(newSource: Option[ProcObjView.Timeline[S]])(implicit tx: S#Tx): InputElem[S] = {
      source.foreach(_.removeTarget(target))
      new InputElem(span = span, source = newSource, target = target, obs = obs, tx0 = tx)
    }
  }

  trait InputAttr[S <: stm.Sys[S]] extends Disposable[S#Tx] {
    def parent: ProcObjView.Timeline[S]
    def key: String
  }

  private trait InputAttrImpl[S <: stm.Sys[S]] extends InputAttr[S] {
    // ---- abstract ----

    protected def viewMap: IdentifierMap[S#ID, S#Tx, Elem]

    protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem]
    protected def elemAddedEDT  (elem: Elem): Unit
    protected def elemRemovedEDT(elem: Elem): Unit

    // ---- impl ----

    final type Elem = InputElem[S]

    // _not_ [this] because Scala 2.10 crashes!
    private /* [this] */ val viewSet = TSet.empty[Elem]  // because `viewMap.iterator` does not exist...

    def paintInputAttr(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering, px1c: Int, px2c: Int): Unit = {
      // println(s"paintInputAttr(${rangeSeq.iterator.size})")
      val canvas  = tlv.canvas
      val pStart  = parent.pStart
      val pStop   = parent.pStop
      val py      = parent.py
      val start   = math.max(pStart, canvas.screenToFrame(px1c - 4).toLong) - pStart
      val stop    = math.min(pStop , canvas.screenToFrame(px2c + 4).toLong) - pStart

      val it      = elemOverlappingEDT(start, stop) // rangeSeq.filterOverlaps((start, stop))
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

         we use linear search here; binary search would be faster, but also more involved,
         and typically we will only have very few elements

       */

      // ---- calculate shapes ----

      var fgSize = 0
      var bgSize = 0
      var fg     = r.intArray1 // pntBackground new Array[Int](16 * 2)
      var bg     = r.intArray2 // new Array[Int](16 * 2)

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
          val fgStart = frameToScreen(start)
          val fgStop  = frameToScreen(stop )

          // 'union'
          var i = 0
          var startFound = false
          while (i < bgSize && !startFound) {
            val bgStop = bg(i + 1)
            if (fgStart <= bgStop) {
              startFound = true
            } else {
              i += 2
            }
          }

          if (i < bgSize) {
            bg(i) = math.min(bg(i), fgStart)
            var j = i + 2
            var stopFound = false
            while (j < bgSize && !stopFound) {
              val bgStart = bg(j)
              if (fgStop < bgStart) {
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
            bg(j + 1) = math.max(bg(j + 1), fgStop)

          } else {
            addToBg(fgStart, fgStop)
          }
        }

        elem.span match {
          case Span(eStart, eStop) =>
            addStart(eStart, elem)
            addStop (eStop )
            addSpan (eStart, eStop)
          case Span.From(eStart) =>
            addStart(eStart, elem)
            // quasi ellipsis
            addSpan(eStart, math.min(stop, eStart + canvas.screenToFrames(16).toLong))
          case Span.Until(eStop) =>
            addStop(eStop)
            addSpan (0L, eStop)
          case _ =>
        }
      }

      // ---- subtract foreground from background ----
      // XXX TODO -- this could go into a generic library, it's a very useful algorithm

      var kk = 0
      while (kk < fgSize) {
        val x   = fg(kk)
        val tpe = fg(kk + 1) & 3
        if (tpe < 3) {  // ignore <stop-cut> because it's redundant with causal <start>
        val fgStart  = if (tpe == 2) x      else x - 3
          val fgStop   = /* if (tpe == 2) x + 3  else */ x + 3

          var i = 0
          var startFound = false
          while (i < bgSize && !startFound) {
            val bgStop = bg(i + 1)
            if (fgStart < bgStop) {
              startFound = true
            } else {
              i += 2
            }
          }

          if (i < bgSize) {
            var j = i
            var stopFound = false
            while (j < bgSize && !stopFound) {
              val bgStart = bg(j)
              if (fgStop < bgStart) {
                stopFound = true
              } else {
                j += 2
              }
            }
            j -= 2

            // cases:
            // i > j --- nothing to be removed
            // i == j
            //   - fgStart == bgStart && fgStop == bgStop: remove
            //   - fgStart >  bgStart && fgStop == bgStop: replace
            //   - fgStart == bgStart && fgStop < bgStop : replace
            //   - fgStart >  bgStart && fgStop < bgStop : replace and insert
            // i < j
            //   - implies fgStop == bgStop
            //   - fgStart == bgStart: remove
            //   - fgStart > bgStart : replace
            //   - process 'inner'
            //   - last: if fgStop < bgStop replace else remove

            if (i <= j) {
              if (i == j && bg(i) < fgStart && bg(i + 1) > fgStop) { // bgSize grows
                j += 2
                val bgIn = if (bgSize == bg.length) {
                  val tmp = bg
                  bg = new Array[Int](bgSize * 2)
                  System.arraycopy(tmp, 0, bg, 0, j)  // include i
                  tmp
                } else bg

                System.arraycopy(bgIn, i, bg, j, bgSize - i)  // include i
                bg(i + 1) = fgStart   // replace-stop
                bg(j    ) = fgStop    // replace-start
                bgSize += 2

              } else {    // bgSize stays the same or shrinks
              var read  = i
                var write = i
                while (read <= j) {
                  val bgStart = bg(read)
                  val bgStop  = bg(read + 1)
                  if (fgStart > bgStart) {
                    bg(read + 1) = fgStart  // replace-stop
                    write += 2
                  } else if (fgStop < bgStop) {
                    bg(read) = fgStop       // replace-start
                    write += 2
                  } // else remove
                  read += 2
                }

                if (write < read) {
                  System.arraycopy(bg, read, bg, write, bgSize - read)
                  bgSize -= read - write
                }
              }
            }
          }
        }

        kk += 2
      }

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

    def dispose()(implicit tx: S#Tx): Unit = {
      import TxnLike.peer
      viewSet.foreach(_.dispose())
      viewSet.clear()
    }

    type Entry <: Identifiable[S#ID]

    protected def mkTarget(entry: Entry)(implicit tx: S#Tx): LinkTarget[S]

    final protected def addAttrIn(span: SpanLike, entry: Entry, value: Obj[S], fire: Boolean)
                                 (implicit tx: S#Tx): Unit =
      value match {
        case out: proc.Output[S] =>
          import TxnLike.peer
          val entryID   = entry.id
          val idH       = tx.newHandle(entryID)
          val viewInit  = parent.context.getAux    [ProcObjView.Timeline[S]](out.id)
          val obs       = parent.context.observeAux[ProcObjView.Timeline[S]](out.id) { implicit tx => upd =>
            val id = idH()
            viewMap.get(id).foreach { elem1 =>
              // elem2 keeps the observer, so no `dispose` call here
              val elem2 = upd match {
                case AuxContext.Added(_, sourceView)  => elem1.copy(Some(sourceView))
                case AuxContext.Removed(_)            => elem1.copy(None)
              }
              elem1.source.foreach(_.removeTarget(elem1.target))
              elem2.source.foreach(_.addTarget   (elem2.target))

              viewMap.put(id, elem2)  // replace
              viewSet.remove(elem1)
              viewSet.add   (elem2)
              deferTx {
                elemRemovedEDT(elem1)
                elemAddedEDT  (elem2)
                // rangeSeq -= elem1
                // rangeSeq += elem2
              }
              parent.fireRepaint()
            }
          }
          val elem0 = new Elem(span, viewInit, mkTarget(entry), obs, tx)
          viewMap.put(entryID, elem0)
          viewSet.add(elem0)
          deferTx {
            elemAddedEDT(elem0)
            // rangeSeq += elem0
          }
          if (fire) parent.fireRepaint()

        case _ => // no others supported ATM
      }

    final protected def removeAttrIn(/* span: SpanLike, */ entryID: S#ID)(implicit tx: S#Tx): Unit =
      viewMap.get(entryID).foreach { elem0 =>
        import TxnLike.peer
        viewMap.remove(entryID)
        viewSet.remove(elem0)
        deferTx {
          elemRemovedEDT(elem0)
         // rangeSeq -= elem0
        }
        elem0.dispose()
        parent.fireRepaint()
      }
  }

  private final class InputAttrOutput[S <: Sys[S]](val parent: ProcObjView.Timeline[S], val key: String,
                                                   out: proc.Output[S], tx0: S#Tx)
    extends InputAttrImpl[S] {

    override def toString: String = s"InputAttrOutput(parent = $parent, key = $key)"

    type Entry = proc.Output[S]

    protected def mkTarget(entry: proc.Output[S])(implicit tx: S#Tx): LinkTarget[S] =
      new LinkTargetOutput[S](this)

    private[this] val outH = tx0.newHandle(out)

    def output(implicit tx: S#Tx): proc.Output[S] = outH()

    protected val viewMap: IdentifierMap[S#ID, S#Tx, Elem] = tx0.newInMemoryIDMap

    // EDT
    private[this] var edtElem: Elem = _

    protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] = Iterator.single(edtElem)

    protected def elemAddedEDT  (elem: Elem): Unit = edtElem = elem
    protected def elemRemovedEDT(elem: Elem): Unit = ()

    // ---- init ----
    addAttrIn(span = Span.From(0L), entry = out, value = out, fire = false)(tx0)
  }

  private final class InputAttrFolder[S <: Sys[S]](val parent: ProcObjView.Timeline[S], val key: String,
                                                   f: proc.Folder[S], tx0: S#Tx)
    extends InputAttrImpl[S] {

    override def toString: String = s"InputAttrFolder(parent = $parent, key = $key)"

    type Entry = Obj[S]

    protected def mkTarget(entry: Obj[S])(implicit tx: S#Tx): LinkTarget[S] =
      new LinkTargetFolder[S](this, tx.newHandle(entry))

    private[this] val fH = tx0.newHandle(f)

    def folder(implicit tx: S#Tx): proc.Folder[S] = fH()

    protected val viewMap: IdentifierMap[S#ID, S#Tx, Elem] = tx0.newInMemoryIDMap

    // EDT
    private[this] var edtSet = Set.empty[Elem]  // XXX TODO --- do we need a multi-set in theory?

    protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] = edtSet.iterator

    protected def elemAddedEDT  (elem: Elem): Unit = edtSet += elem
    protected def elemRemovedEDT(elem: Elem): Unit = edtSet -= elem

    private[this] val observer: Disposable[S#Tx] =
      f.changed.react { implicit tx => upd => upd.changes.foreach {
        case proc.Folder.Added  (_ /* index */, child) =>
          addAttrIn(Span.From(0L), entry = child, value = child, fire = true)
        case proc.Folder.Removed(_ /* index */, child) => removeAttrIn(entryID = child.id)
      }} (tx0)

    override def dispose()(implicit tx: S#Tx): Unit = {
      super.dispose()
      observer.dispose()
    }

    // ---- init ----
    f.iterator(tx0).foreach { child =>
      addAttrIn(Span.From(0L), entry = child, value = child, fire = false)(tx0)
    }
  }

  private final class InputAttrTimeline[S <: Sys[S]](val parent: ProcObjView.Timeline[S], val key: String,
                                                     tl: proc.Timeline[S], tx0: S#Tx)
    extends InputAttrImpl[S] {

    override def toString: String = s"InputAttrTimeline(parent = $parent, key = $key)"

    type Entry = proc.Timeline.Timed[S]

    protected def mkTarget(entry: Entry)(implicit tx: S#Tx): LinkTarget[S] =
      new LinkTargetTimeline[S](this, tx.newHandle(entry.span), tx.newHandle(entry.value))

    private[this] val tlH = tx0.newHandle(tl)

    def timeline(implicit tx: S#Tx): proc.Timeline[S] = tlH()

    protected val viewMap: IdentifierMap[S#ID, S#Tx, Elem] = tx0.newInMemoryIDMap

    // EDT
    private[this] var edtRange = RangedSeq.empty[Elem, Long](_.point, Ordering.Long)

    protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] =
      edtRange.filterOverlaps((start, stop))

    protected def elemAddedEDT  (elem: Elem): Unit = edtRange += elem
    protected def elemRemovedEDT(elem: Elem): Unit = edtRange -= elem

    private[this] val observer: Disposable[S#Tx] =
      tl.changed.react { implicit tx => upd => upd.changes.foreach {
        case proc.Timeline.Added  (span  , entry) =>
          addAttrIn(span, entry = entry, value = entry.value, fire = true)
        case proc.Timeline.Removed(_ /* span */, entry) => removeAttrIn(/* span, */ entryID = entry.id)
        case proc.Timeline.Moved  (spanCh, entry) =>
          removeAttrIn(/* spanCh.before, */ entryID = entry.id)
          addAttrIn   (spanCh.now, entry = entry, value = entry.value, fire = true)
      }} (tx0)

    override def dispose()(implicit tx: S#Tx): Unit = {
      super.dispose()
      observer.dispose()
    }

    // ---- init ----
    tl.iterator(tx0).foreach { case (span, xs) =>
      xs.foreach(entry => addAttrIn(span, entry = entry, value = entry.value, fire = false)(tx0))
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
      implicit val tpe: Type.Expr[AudioCue, AudioCue.Obj] = AudioCue.Obj
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

//  final case class Link[S <: stm.Sys[S]](target: ProcObjView.Timeline[S], targetKey: String)

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

    def addTarget   (tgt: LinkTarget[S])(implicit tx: TxnLike): Unit
    def removeTarget(tgt: LinkTarget[S])(implicit tx: TxnLike): Unit

    def targets(implicit tx: TxnLike): Set[LinkTarget[S]]
  }
}
trait ProcObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Proc[S]

  def objH: stm.Source[S#Tx, Proc[S]]
}