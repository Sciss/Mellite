/*
 *  DoubleObjView.scala
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

import de.sciss.desktop
import de.sciss.kollflitz.Vec
import de.sciss.lucre.expr.{DoubleObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{PrimitiveConfig, primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.{Icon, SpinnerNumberModel}

import scala.swing.{Component, Graphics2D, Label}
import scala.util.Try

object DoubleObjView extends ListObjView.Factory with GraphemeObjView.Factory {
  type E[S <: stm.Sys[S]]       = DoubleObj[S]
  type V                        = Double
  val icon          : Icon      = raphaelIcon(Shapes.RealNumber)
  val prefix        : String    = "Double"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = DoubleObj
  def category      : String    = ObjView.categPrimitives
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: E[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleObj.Var(_) => true
      case _                => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
  }

  type Config[S <: stm.Sys[S]] = PrimitiveConfig[V]

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val model     = new SpinnerNumberModel(0.0, Double.NegativeInfinity, Double.PositiveInfinity, 1.0)
    val ggValue   = new Spinner(model)
    val res = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Some(model.getNumber.doubleValue))
    res.foreach(ok(_))
  }

  def makeObj[S <: Sys[S]](config: (String, V))(implicit tx: S#Tx): List[Obj[S]] = {
    val (name, value) = config
    val obj = DoubleObj.newVar(DoubleObj.newConst[S](value))
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

    final def factory: ObjView.Factory = DoubleObjView

    final def exprType: Type.Expr[V, E] = DoubleObj

    final def expr(implicit tx: S#Tx): E[S] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[S <: Sys[S]](objH: stm.Source[S#Tx, E[S]], var value: Double,
                                            override val isEditable: Boolean, isViewable: Boolean)
    extends Impl(objH, isViewable = isViewable) with ListObjView[S]
      with ListObjViewImpl.SimpleExpr[S, V, E] {

    def configureRenderer(label: Label): Component = {
      label.text = value.toFloat.toString   // avoid excessive number of digits!
      label
    }

    def convertEditValue(v: Any): Option[Double] = v match {
      case num: V       => Some(num)
      case s  : String  => Try(s.toDouble).toOption
    }
  }

  // ---- GraphemeObjView ----

  def graphemePaintFront[S <: Sys[S]](view: GraphemeObjView[S], value: Double, g: Graphics2D,
                                      gv: GraphemeView[S], r: GraphemeRendering): Unit = {
    import GraphemeObjView.{HandleDiameter, HandleRadius}
    val c   = gv.canvas
    val jc  = c.canvasComponent.peer
    val h   = jc.getHeight
    val x   = c.frameToScreen(view.timeValue)
    val y   = (1 - value) * (h - 1)
    val selected = gv.selectionModel.contains(view)
    val p = r.ellipse1
    p.setFrame(x - 2, y - 2, 4, 4)
    g.setPaint(if (selected) r.pntRegionBackgroundSelected else r.pntRegionBackground)
    g.fill(p)
    p.setFrame(x - HandleRadius, y - HandleRadius, HandleDiameter, HandleDiameter)
    g.setPaint(if (selected) r.pntRegionOutlineSelected else r.pntRegionOutline)
    g.draw(p)
  }

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, E[S]],
                                                value: V,
                                                isViewable: Boolean)
    extends Impl[S](objH, isViewable = isViewable)
    with GraphemeObjViewImpl.BasicImpl[S]
    with GraphemeObjView.HasStartLevels[S] {

    def insets: Insets = GraphemeObjView.DefaultInsets

    def startLevels: Vec[Double] = value +: Vector.empty

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit =
      graphemePaintFront(this, value, g, gv, r)
  }
}
