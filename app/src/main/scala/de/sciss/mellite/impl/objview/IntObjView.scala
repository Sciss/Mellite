/*
 *  IntObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.lucre.{Expr, IntObj, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.{ObjListView, ObjView, Shapes}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Confluent, Universe}
import de.sciss.swingplus.Spinner

import javax.swing.{Icon, SpinnerNumberModel}
import scala.util.{Success, Try}

object IntObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = IntObj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.IntegerNumber)
  val prefix        : String    = "Int"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = IntObj
  def category      : String    = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: IntObj[T])(implicit tx: T): IntObjView[T] with ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case IntObj.Var(_)  => true
      case _              => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl(tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: Int, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val model   = new SpinnerNumberModel(0, Int.MinValue, Int.MaxValue, 1)
    val ggValue = new Spinner(model)
    val res0    = ObjViewImpl.primitiveConfig[T, Int](window, tpe = prefix, ggValue = ggValue,
      prepare = Success(model.getNumber.intValue()))
    val res     = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
//    // cf. https://github.com/scallop/scallop/issues/189
//    val args1 = args match {
//      case ("--help" | "-h") :: Nil => args
//      case _ => "--ignore" +: args
//    }
    object p extends ObjViewCmdLineParser[Config[T]](this, args /*args1*/) {
//      val ignore: Opt[Boolean]  = opt(hidden = true)
      val const: Opt[Boolean]   = opt(descr = s"Make constant instead of variable")
      val value: Opt[Int]       = trailArg(descr = "Initial int value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = IntObj.newConst[T](value)
    val obj   = if (const) obj0 else IntObj.newVar(obj0)
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, IntObj[T]],
                                            var value: Int,
                                            override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends IntObjView[T]
    with ObjListView[T]
    with ObjViewImpl.Impl[T]
    with ObjListViewImpl.SimpleExpr[T, Int, IntObj]
    with ObjListViewImpl.StringRenderer {

    def factory: ObjView.Factory = IntObjView

    def exprType: Expr.Type[Int, IntObj] = IntObj

    def expr(implicit tx: T): IntObj[T] = obj

    def convertEditValue(v: Any): Option[Int] = v match {
      case num: Int     => Some(num)
      case s  : String  => Try(s.toInt).toOption
      case _            => None
    }
  }
}
trait IntObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = IntObj[T]
}