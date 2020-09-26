/*
 *  InputAttrImpl.scala
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

import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.{Identifiable, IdentifierMap, Obj, TxnLike}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.mellite.{TimelineRendering, TimelineView}
import de.sciss.mellite.impl.proc.ProcObjView.{InputAttr, LinkTarget}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc
import de.sciss.synth.proc.AuxContext

import scala.annotation.switch
import scala.concurrent.stm.TSet
import scala.swing.Graphics2D

trait InputAttrImpl[T <: LTxn[T]] extends InputAttr[T] {
  // ---- abstract ----

  protected def viewMap: IdentifierMap[S#Id, T, Elem]

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem]
  protected def elemAddedEDT  (elem: Elem): Unit
  protected def elemRemovedEDT(elem: Elem): Unit

  // ---- impl ----

  final type Elem = InputElem[T]

  // _not_ [this] because Scala 2.10 crashes!
  private /* [this] */ val viewSet = TSet.empty[Elem]  // because `viewMap.iterator` does not exist...

  def paintInputAttr(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering, px1c: Int, px2c: Int): Unit = {
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

  def dispose()(implicit tx: T): Unit = {
    import TxnLike.peer
    viewSet.foreach(_.dispose())
    viewSet.clear()
  }

  type Entry <: Identifiable[S#Id]

  protected def mkTarget(entry: Entry)(implicit tx: T): LinkTarget[T]

  final protected def addAttrIn(span: SpanLike, entry: Entry, value: Obj[T], fire: Boolean)
                               (implicit tx: T): Unit =
    value match {
      case out: proc.Output[T] =>
        import TxnLike.peer
        val entryId   = entry.id
        val idH       = tx.newHandle(entryId)
        val viewInit  = parent.context.getAux    [ProcObjView.Timeline[T]](out.id)
        val obs       = parent.context.observeAux[ProcObjView.Timeline[T]](out.id) { implicit tx => upd =>
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
        viewMap.put(entryId, elem0)
        viewSet.add(elem0)
        deferTx {
          elemAddedEDT(elem0)
          // rangeSeq += elem0
        }
        if (fire) parent.fireRepaint()

      case _ => // no others supported ATM
    }

  final protected def removeAttrIn(/* span: SpanLike, */ entryId: S#Id)(implicit tx: T): Unit =
    viewMap.get(entryId).foreach { elem0 =>
      import TxnLike.peer
      viewMap.remove(entryId)
      viewSet.remove(elem0)
      deferTx {
        elemRemovedEDT(elem0)
        // rangeSeq -= elem0
      }
      elem0.dispose()
      parent.fireRepaint()
    }
}
