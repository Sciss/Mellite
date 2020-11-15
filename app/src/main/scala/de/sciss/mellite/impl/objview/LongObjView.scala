/*
 *  LongObjView.scala
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
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Expr, LongObj, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.{ObjListView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.proc.Implicits._
import de.sciss.proc.{Confluent, Universe}
import javax.swing.{Icon, SpinnerNumberModel}

import scala.util.{Success, Try}

object LongObjView extends ObjListView.Factory {
  type E[T <: LTxn[T]] = LongObj[T]
  val icon          : Icon      = raphaelIcon(Shapes.IntegerNumber)  // XXX TODO
  val prefix        : String   = "Long"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = LongObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: LongObj[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case LongObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[T](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: Long, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val model     = new SpinnerNumberModel(0L, Long.MinValue, Long.MaxValue, 1L)
    val ggValue   = new Spinner(model)
    val res0 = primitiveConfig[T, Long](window, tpe = prefix, ggValue = ggValue, prepare =
      Success(model.getNumber.longValue()))
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[Long]    = trailArg(descr = "Initial long value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = LongObj.newConst[T](value)
    val obj   = if (const) obj0 else LongObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, LongObj[T]],
                                var value: Long,
                                override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends ObjListView /* .Long */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.SimpleExpr[T, Long, LongObj]
      with ObjListViewImpl.StringRenderer {

    type Repr = LongObj[T]

    def factory: ObjView.Factory = LongObjView

    def exprType: Expr.Type[Long, LongObj] = LongObj

    def expr(implicit tx: T): LongObj[T] = objH()

    def convertEditValue(v: Any): Option[Long] = v match {
      case num: Long => Some(num)
      case s: String => Try(s.toLong).toOption
    }
  }
}
