/*
 *  EnvSegmentObjView.scala
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
package gui.impl

import java.awt.geom.Area
import javax.swing.Icon

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.expr.Type
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewImpl.raphaelIcon
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView}
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.{EnvSegment, Workspace}

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

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = ()

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

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, E[S]],
                                                value: V)
    extends Impl[S](objH)
      with GraphemeObjViewImpl.BasicImpl[S] {

    private[this] val allSame =
      value.numChannels <= 1 || { val v0 = value.startLevels; val vh = v0.head; v0.forall(_ == vh) }

    def insets: Insets = Insets(4, 4, 4, 4)

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = {
      if (value.numChannels == 0) return
      val levels = value.startLevels
      if (allSame) {
        DoubleObjView.graphemePaintFront(this, levels.head, g, gv, r)
        return
      }

      val c   = gv.canvas
      val jc  = c.canvasComponent.peer
      val h   = jc.getHeight
      val x   = c.frameToScreen(timeValue)

      val a1  = r.area1
      val a2  = r.area2
      val p   = r.ellipse1 // r.shape1
      a1.reset()
      a2.reset()
      val hm  = h - 1
      var i   = 0
      var min = Double.MaxValue
      var max = Double.MinValue
      while (i < levels.size) {
        val v = levels(i)
        i += 1
        val y = v * hm
        p.setFrame(x - 2, y - 2, 4, 4)
        a1.add(new Area(p))
        p.setFrame(x - 3.5, y - 3.5, 7.0, 7.0)
        a2.add(new Area(p))
        if (y < min) min = y
        if (y > max) max = y
      }

      g.setStroke(r.strokeInletSpan)
      g.setPaint(r.pntInletSpan)
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