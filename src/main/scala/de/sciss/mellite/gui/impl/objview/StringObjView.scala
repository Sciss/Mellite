/*
 *  StringObjView.scala
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
import de.sciss.icons.raphael
import de.sciss.lucre.expr.{StringObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{ListObjView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.TextField
import scala.util.Success

object StringObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = StringObj[~]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Font)
  val prefix        : String   = "String"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = StringObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: StringObj[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case StringObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: String, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue   = new TextField(20)
    ggValue.text  = "Value"
    val res0 = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Success(ggValue.text))
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String]): MakeResult[S] = {
    val default: Config[S] = Config(value = "")
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[String]("value")
      .text("Initial string value")
      .required()
      .action((v, c) => c.copy(value = v))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = StringObj.newConst[S](value)
    val obj   = if (const) obj0 else StringObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, StringObj[S]],
                                var value: String,
                                override val isEditable: Boolean, val isViewable: Boolean)
    extends ListObjView[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, String, StringObj]
      with ListObjViewImpl.StringRenderer {

    type E[~ <: stm.Sys[~]] = StringObj[~]

    def factory: ObjView.Factory = StringObjView

    def exprType: Type.Expr[String, StringObj] = StringObj

    def convertEditValue(v: Any): Option[String] = Some(v.toString)

    def expr(implicit tx: S#Tx): StringObj[S] = objH()
  }
}
