/*
 *  DoubleVectorObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.kollflitz.Vec
import de.sciss.lucre.expr.{DoubleVector, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{PrimitiveConfig, primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView, Shapes}
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.{Component, Graphics2D, Label, TextField}
import scala.util.Try

object DoubleVectorObjView extends ListObjView.Factory with GraphemeObjView.Factory {
  type E[S <: stm.Sys[S]]       = DoubleVector[S]
  type V                        = Vec[Double]
  val icon          : Icon      = raphaelIcon(Shapes.RealNumberVector)
  val prefix        : String    = "DoubleVector"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = DoubleVector
  def category      : String    = ObjView.categPrimitives
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: E[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleVector.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
  }

  type Config[S <: stm.Sys[S]] = PrimitiveConfig[V]

  private def parseString(s: String): Option[V] =
    Try(s.split(" ").iterator.map(x => x.trim().toDouble).toIndexedSeq).toOption

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue = new TextField("0.0 0.0")
    val res = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = parseString(ggValue.text))
    res.foreach(ok(_))
  }

  def makeObj[S <: Sys[S]](config: (String, V))(implicit tx: S#Tx): List[Obj[S]] = {
    val (name, value) = config
    val obj = DoubleVector.newVar(DoubleVector.newConst[S](value))
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  def mkGraphemeView[S <: Sys[S]](entry: Entry[S], value: E[S], mode: GraphemeView.Mode)
                                 (implicit tx: S#Tx): GraphemeObjView[S] = {
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new GraphemeImpl[S](tx.newHandle(entry), tx.newHandle(value), value = value.value, isViewable = isViewable)
      .initAttrs(value).initAttrs(entry)
  }

  // ---- basic ----

  private abstract class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, E[S]], val isViewable: Boolean)
    extends ObjViewImpl.Impl[S]
      with ObjViewImpl.ExprLike[S, V, E] {

    final def factory: ObjView.Factory = DoubleVectorObjView

    final def exprType: Type.Expr[V, E] = DoubleVector

    final def expr(implicit tx: S#Tx): E[S] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[S <: Sys[S]](objH: stm.Source[S#Tx, E[S]], var value: V,
                                    override val isEditable: Boolean, isViewable: Boolean)
    extends Impl(objH, isViewable = isViewable) with ListObjView[S]
      with ListObjViewImpl.SimpleExpr[S, V, E] {

    def convertEditValue(v: Any): Option[V] = v match {
      case num: Vec[_] => num.foldLeft(Option(Vector.empty[Double])) {
        case (Some(prev), d: Double) => Some(prev :+ d)
        case _ => None
      }
      case s: String  => parseString(s)
    }

    def configureRenderer(label: Label): Component = {
      label.text = value.iterator.map(_.toFloat).mkString(" ")  // avoid excessive number of digits!
      label
    }
  }

  // ---- GraphemeObjView ----

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, E[S]],
                                                value: V,
                                                isViewable: Boolean)
    extends Impl[S](objH, isViewable = isViewable)
      with GraphemeObjViewImpl.BasicImpl[S]
      with GraphemeObjView.HasStartLevels[S] {

    private[this] val allSame = value.size <= 1 || { val v0 = value.head; value.forall(_ == v0) }

    def insets: Insets = GraphemeObjView.DefaultInsets

    def startLevels: Vec[Double] = value

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = {
      import GraphemeObjView.{HandleDiameter, HandleRadius}

      if (value.isEmpty) return
      if (allSame) {
        DoubleObjView.graphemePaintFront(this, value.head, g, gv, r)
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
      while (i < value.size) {
        val v = value(i)
        i += 1
        val y = v * hm
        p.setFrame(x - 2, y - 2, 4, 4)
        a1.add(new Area(p))
        p.setFrame(x - HandleRadius, y - HandleRadius, HandleDiameter, HandleDiameter)
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
