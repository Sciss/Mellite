/*
 *  StringObjView.scala
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
import de.sciss.icons.raphael
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, StringObj, Txn => LTxn}
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.TextField
import scala.util.Success

object StringObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = StringObj[~]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Font)
  val prefix        : String   = "String"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = StringObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: StringObj[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case StringObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[T](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: String, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val ggValue   = new TextField(20)
    ggValue.text  = "Value"
    val res0 = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Success(ggValue.text))
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[String]  = trailArg(descr = "Initial string value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = StringObj.newConst[T](value)
    val obj   = if (const) obj0 else StringObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, StringObj[T]],
                                var value: String,
                                override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.SimpleExpr[T, String, StringObj]
      with ObjListViewImpl.StringRenderer {

    type Repr = StringObj[T]

    def factory: ObjView.Factory = StringObjView

    def exprType: Expr.Type[String, StringObj] = StringObj

    def convertEditValue(v: Any): Option[String] = Some(v.toString)

    def expr(implicit tx: T): StringObj[T] = objH()
  }
}
