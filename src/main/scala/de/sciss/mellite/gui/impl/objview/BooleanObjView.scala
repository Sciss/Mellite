/*
 *  BooleanObjView.scala
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
import de.sciss.lucre.expr.BooleanObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{ListObjView, ObjView, Shapes}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.CheckBox
import scala.util.Success

object BooleanObjView extends ListObjView.Factory {
  type E[S <: stm.Sys[S]] = BooleanObj[S]
  val icon          : Icon      = raphaelIcon(Shapes.BooleanNumber)
  val prefix        : String   = "Boolean"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = BooleanObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean  = true

  def mkListView[S <: Sys[S]](obj: BooleanObj[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case BooleanObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Boolean, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue = new CheckBox()
    val res0 = primitiveConfig[S, Boolean](window, tpe = prefix, ggValue = ggValue, prepare = Success(ggValue.selected))
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = Config(value = false)
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[Boolean]("value")
      .text("Initial boolean value (0, 1, false, true)")
      .required()
      .action((v, c) => c.copy(value = v))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = BooleanObj.newConst[S](value)
    val obj   = if (const) obj0 else BooleanObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, BooleanObj[S]],
                                var value: Boolean,
                                override val isEditable: Boolean, val isViewable: Boolean)
    extends ListObjView /* .Boolean */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.BooleanExprLike[S]
      with ListObjViewImpl.SimpleExpr[S, Boolean, BooleanObj] {

    type E[~ <: stm.Sys[~]] = BooleanObj[~]

    def factory: ObjView.Factory = BooleanObjView

    def expr(implicit tx: S#Tx): BooleanObj[S] = objH()
  }
}
