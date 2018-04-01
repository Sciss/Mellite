/*
 *  IntObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.lucre.expr.{IntObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{ListObjView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.{Confluent, Workspace}
import de.sciss.synth.proc.Implicits._

import scala.util.Try

object IntObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = IntObj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.IntegerNumber)
  val prefix        : String    = "Int"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = IntObj
  def category      : String    = ObjView.categPrimitives
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: IntObj[S])(implicit tx: S#Tx): IntObjView[S] with ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case IntObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl(tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).initAttrs(obj)
  }

  type Config[S <: stm.Sys[S]] = ObjViewImpl.PrimitiveConfig[Int]

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = {
    val model     = new SpinnerNumberModel(0, Int.MinValue, Int.MaxValue, 1)
    val ggValue   = new Spinner(model)
    val res = ObjViewImpl.primitiveConfig[S, Int](window, tpe = prefix, ggValue = ggValue,
      prepare = Some(model.getNumber.intValue()))
    res.foreach(ok(_))
  }

  def makeObj[S <: Sys[S]](config: (String, Int))(implicit tx: S#Tx): List[Obj[S]] = {
    val (name, value) = config
    val obj = IntObj.newVar(IntObj.newConst[S](value))
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, IntObj[S]],
                                var value: Int,
                                override val isEditable: Boolean, val isViewable: Boolean)
    extends IntObjView[S]
    with ListObjView[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.SimpleExpr[S, Int, IntObj]
    with ListObjViewImpl.StringRenderer {

    override def obj(implicit tx: S#Tx): IntObj[S] = objH()

    type E[~ <: stm.Sys[~]] = IntObj[~]

    def factory: ObjView.Factory = IntObjView

    def exprType: Type.Expr[Int, IntObj] = IntObj

    def expr(implicit tx: S#Tx): IntObj[S] = obj

    def convertEditValue(v: Any): Option[Int] = v match {
      case num: Int     => Some(num)
      case s  : String  => Try(s.toInt).toOption
    }
  }
}
trait IntObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): IntObj[S]
}