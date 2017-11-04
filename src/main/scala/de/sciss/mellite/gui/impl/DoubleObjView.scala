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

import javax.swing.{Icon, SpinnerNumberModel}

import de.sciss.desktop
import de.sciss.lucre.expr.{DoubleObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewImpl.{PrimitiveConfig, primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{ListObjView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Workspace}

import scala.util.Try

object DoubleObjView extends ListObjView.Factory {
  type E[S <: stm.Sys[S]] = DoubleObj[S]
  val icon: Icon          = raphaelIcon(Shapes.RealNumber)
  val prefix              = "Double"
  def humanName: String  = prefix
  def tpe: Obj.Type       = DoubleObj
  def hasMakeDialog       = true
  def category: String   = ObjView.categPrimitives

  def mkListView[S <: Sys[S]](obj: DoubleObj[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleObj.Var(_)  => true
      case _            => false
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

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, DoubleObj[S]], var value: Double,
                                override val isEditable: Boolean, val isViewable: Boolean)
    extends ListObjView /* .Double */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, Double, DoubleObj]
      with ListObjViewImpl.StringRenderer {

    type E[~ <: stm.Sys[~]] = DoubleObj[~]

    def factory: ObjView.Factory = DoubleObjView

    val exprType: Type.Expr[Double, DoubleObj] = DoubleObj

    def expr(implicit tx: S#Tx): DoubleObj[S] = objH()

    def convertEditValue(v: Any): Option[Double] = v match {
      case num: Double => Some(num)
      case s: String => Try(s.toDouble).toOption
    }
  }
}