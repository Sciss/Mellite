/*
 *  LongObjView.scala
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
import de.sciss.lucre.expr.{LongObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{ObjListView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.{Icon, SpinnerNumberModel}

import scala.util.{Success, Try}

object LongObjView extends ObjListView.Factory {
  type E[S <: stm.Sys[S]] = LongObj[S]
  val icon          : Icon      = raphaelIcon(Shapes.IntegerNumber)  // XXX TODO
  val prefix        : String   = "Long"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = LongObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: LongObj[S])(implicit tx: S#Tx): ObjListView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case LongObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[S](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Long, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val model     = new SpinnerNumberModel(0L, Long.MinValue, Long.MaxValue, 1L)
    val ggValue   = new Spinner(model)
    val res0 = primitiveConfig[S, Long](window, tpe = prefix, ggValue = ggValue, prepare =
      Success(model.getNumber.longValue()))
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = Config(value = 0L)
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[Long]("value")
      .text("Initial long value")
      .required()
      .action((v, c) => c.copy(value = v))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = LongObj.newConst[S](value)
    val obj   = if (const) obj0 else LongObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, LongObj[S]],
                                var value: Long,
                                override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends ObjListView /* .Long */[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.SimpleExpr[S, Long, LongObj]
      with ObjListViewImpl.StringRenderer {

    type Repr = LongObj[S]

    def factory: ObjView.Factory = LongObjView

    def exprType: Type.Expr[Long, LongObj] = LongObj

    def expr(implicit tx: S#Tx): LongObj[S] = objH()

    def convertEditValue(v: Any): Option[Long] = v match {
      case num: Long => Some(num)
      case s: String => Try(s.toLong).toOption
    }
  }
}
