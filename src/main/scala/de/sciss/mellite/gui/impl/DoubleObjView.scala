/*
 *  DoubleObjView.scala
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

import java.awt.Color
import javax.swing.{Icon, SpinnerNumberModel}

import de.sciss.desktop
import de.sciss.lucre.expr.{DoubleObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewImpl.{PrimitiveConfig, primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Workspace}

import scala.swing.Graphics2D
import scala.util.Try

object DoubleObjView extends ListObjView.Factory with GraphemeObjView.Factory {
  type E[S <: stm.Sys[S]] = DoubleObj[S]
  val icon          : Icon      = raphaelIcon(Shapes.RealNumber)
  val prefix        : String    = "Double"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = DoubleObj
  def category      : String    = ObjView.categPrimitives
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: DoubleObj[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleObj.Var(_) => true
      case _                => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
  }

  type Config[S <: stm.Sys[S]] = PrimitiveConfig[Double]

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = {
    val model     = new SpinnerNumberModel(0.0, Double.NegativeInfinity, Double.PositiveInfinity, 1.0)
    val ggValue   = new Spinner(model)
    val res = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Some(model.getNumber.doubleValue))
    res.foreach(ok(_))
  }

  def makeObj[S <: Sys[S]](config: (String, Double))(implicit tx: S#Tx): List[Obj[S]] = {
    val (name, value) = config
    val obj = DoubleObj.newVar(DoubleObj.newConst[S](value))
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  def mkGraphemeView[S <: Sys[S]](entry: Entry[S], value: DoubleObj[S], mode: GraphemeView.Mode)
                                 (implicit tx: S#Tx): GraphemeObjView[S] = {
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new GraphemeImpl[S](tx.newHandle(entry), tx.newHandle(value), value = value.value, isViewable = isViewable)
      .initAttrs(value).initAttrs(entry)
  }

  // ---- basic ----

  private abstract class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, DoubleObj[S]], val isViewable: Boolean)
    extends ObjViewImpl.Impl[S]
    with ObjViewImpl.ExprLike[S, Double, DoubleObj] {

    final def factory: ObjView.Factory = DoubleObjView

    final def exprType: Type.Expr[Double, DoubleObj] = DoubleObj

    final def expr(implicit tx: S#Tx): DoubleObj[S] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[S <: Sys[S]](objH: stm.Source[S#Tx, DoubleObj[S]], var value: Double,
                                            override val isEditable: Boolean, isViewable: Boolean)
    extends Impl(objH, isViewable = isViewable) with ListObjView[S]
      with ListObjViewImpl.SimpleExpr[S, Double, DoubleObj]
      with ListObjViewImpl.StringRenderer {

    def convertEditValue(v: Any): Option[Double] = v match {
      case num: Double => Some(num)
      case s  : String => Try(s.toDouble).toOption
    }
  }

  // ---- GraphemeObjView ----

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, DoubleObj[S]],
                                                private var value: Double,
                                                isViewable: Boolean)
    extends Impl[S](objH, isViewable = isViewable)
    with GraphemeObjViewImpl.BasicImpl[S] {

    def insets: Insets = Insets(4, 4, 4, 4)

    override def paintBack (g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = {
      val c   = gv.canvas
      val x   = c.frameToScreen(timeValue)
      val xi  = x.toInt
      val h   = c.canvasComponent.peer.getHeight
      g.setColor(Color.red)
      g.drawLine(xi, 0, xi, h - 1)
      g.drawString(f"$value%g", xi + 4, 12)
    }
  }
}