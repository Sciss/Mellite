/*
 *  IntObjView.scala
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

import de.sciss.desktop
import de.sciss.lucre.expr.{IntObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ObjView
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.{ObjListView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.{Icon, SpinnerNumberModel}

import scala.util.{Success, Try}

object IntObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = IntObj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.IntegerNumber)
  val prefix        : String    = "Int"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = IntObj
  def category      : String    = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: IntObj[S])(implicit tx: S#Tx): IntObjView[S] with ObjListView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case IntObj.Var(_)  => true
      case _              => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl(tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Int, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val model   = new SpinnerNumberModel(0, Int.MinValue, Int.MaxValue, 1)
    val ggValue = new Spinner(model)
    val res0    = ObjViewImpl.primitiveConfig[S, Int](window, tpe = prefix, ggValue = ggValue,
      prepare = Success(model.getNumber.intValue()))
    val res     = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
//    // cf. https://github.com/scallop/scallop/issues/189
//    val args1 = args match {
//      case ("--help" | "-h") :: Nil => args
//      case _ => "--ignore" +: args
//    }
    object p extends ObjViewCmdLineParser[Config[S]](this, args /*args1*/) {
//      val ignore: Opt[Boolean]  = opt(hidden = true)
      val const: Opt[Boolean]   = opt(descr = s"Make constant instead of variable")
      val value: Opt[Int]       = trailArg(descr = "Initial int value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = IntObj.newConst[S](value)
    val obj   = if (const) obj0 else IntObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, IntObj[S]],
                                            var value: Int,
                                            override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends IntObjView[S]
    with ObjListView[S]
    with ObjViewImpl.Impl[S]
    with ObjListViewImpl.SimpleExpr[S, Int, IntObj]
    with ObjListViewImpl.StringRenderer {

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
  type Repr = IntObj[S]
}