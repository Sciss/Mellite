/*
 *  EnvSegmentObjView.scala
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

package de.sciss.mellite.gui.impl.objview

import java.awt.geom.Area

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.kollflitz.Vec
import de.sciss.lucre.expr.Type
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeObjView.HasStartLevels
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView}
import de.sciss.synth.Curve
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.{EnvSegment, Universe}
import javax.swing.Icon

import scala.swing.Graphics2D

object EnvSegmentObjView extends ListObjView.Factory with GraphemeObjView.Factory {
  type E[S <: stm.Sys[S]]       = EnvSegment.Obj[S]
  type V                        = EnvSegment
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Connect)
  val prefix        : String    = "EnvSegment"
  def humanName     : String    = "Envelope Segment"
  def tpe           : Obj.Type  = EnvSegment.Obj
  def category      : String    = ObjView.categPrimitives
  def hasMakeDialog : Boolean   = false // true

  def mkListView[S <: Sys[S]](obj: E[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex    = obj
    val value = ex.value
    new ListImpl[S](tx.newHandle(obj), value).init(obj)
  }

  type Config[S <: stm.Sys[S]] = Unit

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = ()

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = Nil

  def mkGraphemeView[S <: Sys[S]](entry: Entry[S], value: E[S], mode: GraphemeView.Mode)
                                 (implicit tx: S#Tx): GraphemeObjView[S] = {
    new GraphemeImpl[S](tx.newHandle(entry), tx.newHandle(value), value = value.value)
      .initAttrs(value).initAttrs(entry)
  }

  // ---- basic ----

  private abstract class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, E[S]])
    extends ObjViewImpl.Impl[S]
      with ObjViewImpl.ExprLike[S, V, E] {

    final def isViewable: Boolean = false

    final def factory: ObjView.Factory = EnvSegmentObjView

    final def exprType: Type.Expr[V, E] = EnvSegment.Obj

    final def expr(implicit tx: S#Tx): E[S] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[S <: Sys[S]](objH: stm.Source[S#Tx, E[S]], var value: V)
    extends Impl(objH) with ListObjView[S]
      with ListObjViewImpl.SimpleExpr[S, V, E]
//      with ListObjViewImpl.NonEditable[S]
      with ListObjViewImpl.StringRenderer {

    def convertEditValue(v: Any): Option[V] = None

    def isEditable: Boolean = false
  }

  // ---- GraphemeObjView ----

//  private final class SegmentEnd[S <: Sys[S]](val frame: Long, val view: ScalarOptionView[S], obs: Disposable[S#Tx])
//    extends Disposable[S#Tx] {
//
//    def dispose()(implicit tx: S#Tx): Unit = obs.dispose()
//  }

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, E[S]],
                                                value: V)
    extends Impl[S](objH)
      with GraphemeObjViewImpl.BasicImpl[S]
      with GraphemeObjView.HasStartLevels[S] {

    private[this] val allSame =
      value.numChannels <= 1 || { val v0 = value.startLevels; val vh = v0.head; v0.forall(_ == vh) }

    def startLevels: Vec[Double] = value.startLevels

    def insets: Insets = Insets(4, 4, 4, 4)

    private[this] var succOpt = Option.empty[HasStartLevels[S]]

    override def succ_=(opt: Option[GraphemeObjView[S]])(implicit tx: S#Tx): Unit = deferTx {
      succOpt = opt.collect {
        case hs: HasStartLevels[S] => hs
      }
      // XXX TODO --- fire repaint?
    }

    override def paintBack(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = succOpt match {
      case Some(succ) =>
        val startLvl  = value.startLevels
        val endLvl    = succ .startLevels
        val numChS    = startLvl.size
        val numChE    = endLvl  .size
        if (numChS == 0 || numChE == 0) return

        val numCh     = math.max(numChS, numChE)
        val c         = gv.canvas
        val x1        = c.frameToScreen(this.timeValue)
        val x2        = c.frameToScreen(succ.timeValue)
        val jc        = c.canvasComponent.peer
        val h         = jc.getHeight
        val hm        = h - 1
        g.setStroke(r.strokeInletSpan)
        g.setPaint (r.pntInletSpan)
        val path      = r.shape1
        path.reset()

        var ch = 0
        while (ch < numCh) {
          val v1 = startLvl(ch % numChS)
          val y1 = (1 - v1) * hm
          val v2 = endLvl  (ch % numChE)
          val y2 = (1 - v2) * hm
          path.moveTo(x1, y1)

          value.curve match {
            case Curve.linear =>
            case Curve.step   =>
              path.lineTo(x2, y1)

            case curve =>
              var x   = x1 + 4
              val y1f = y1.toFloat
              val y2f = y2.toFloat
              val dx  = x2 - x1
              if (dx > 0) while (x < x2) {
                val pos = ((x - x1) / dx).toFloat
                val y = curve.levelAt(pos, y1f, y2f)
                path.lineTo(x, y)
                x += 4
              }
              // XXX TODO

          }

          path.lineTo(x2, y2)
          ch += 1
        }
        g.draw(path)

      case _ =>
    }

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = {
      if (value.numChannels == 0) return
      val levels = value.startLevels
      if (allSame) {
        DoubleObjView.graphemePaintFront(this, levels.head, g, gv, r)
        return
      }

      val c     = gv.canvas
      val jc    = c.canvasComponent.peer
      val h     = jc.getHeight
      val x     = c.frameToScreen(timeValue)

      val a1    = r.area1
      val a2    = r.area2
      val p     = r.ellipse1 // r.shape1
      a1.reset()
      a2.reset()
      val hm    = h - 1
      var ch    = 0
      val numCh = levels.size
      var min   = Double.MaxValue
      var max   = Double.MinValue
      while (ch < numCh) {
        val v = levels(ch)
        val y = (1 - v) * hm
        p.setFrame(x - 2, y - 2, 4, 4)
        a1.add(new Area(p))
        p.setFrame(x - 3.5, y - 3.5, 7.0, 7.0)
        a2.add(new Area(p))
        if (y < min) min = y
        if (y > max) max = y
        ch += 1
      }

      g.setStroke(r.strokeInletSpan)
      g.setPaint (r.pntInletSpan)
      val ln = r.shape1
      ln.reset()
      ln.moveTo(x, min)
      ln.lineTo(x, max)
      g.draw(ln)
      g.setStroke(r.strokeNormal)
      val selected = gv.selectionModel.contains(this)
      g.setPaint(if (selected) r.pntRegionBackgroundSelected else r.pntRegionBackground)
      g.fill(a1)
      g.setPaint(if (selected) r.pntRegionOutlineSelected else r.pntRegionOutline)
      g.draw(a2)
    }
  }
}
