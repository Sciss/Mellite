/*
 *  BooleanObjView.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.desktop
import de.sciss.lucre.expr.BooleanObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.Shapes
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.CheckBox
import scala.util.Success

object BooleanObjView extends ObjListView.Factory {
  type E[S <: stm.Sys[T]] = BooleanObj[T]
  val icon          : Icon      = raphaelIcon(Shapes.BooleanNumber)
  val prefix        : String   = "Boolean"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = BooleanObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean  = true

  def mkListView[T <: Txn[T]](obj: BooleanObj[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case BooleanObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[T](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[T]](name: String = prefix, value: Boolean, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val ggValue = new CheckBox()
    val res0 = primitiveConfig[T, Boolean](window, tpe = prefix, ggValue = ggValue, prepare = Success(ggValue.selected))
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[Boolean] = boolArg (descr = "Initial boolean value (0, 1, false, true, F, T)")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = BooleanObj.newConst[T](value)
    val obj   = if (const) obj0 else BooleanObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, BooleanObj[T]],
                                var value: Boolean,
                                override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends ObjListView /* .Boolean */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.BooleanExprLike[T]
      with ObjListViewImpl.SimpleExpr[T, Boolean, BooleanObj] {

    type Repr = BooleanObj[T]

    def factory: ObjView.Factory = BooleanObjView

    def expr(implicit tx: T): BooleanObj[T] = objH()
  }
}
