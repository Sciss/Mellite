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
import de.sciss.lucre.expr.{StringObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.TextField
import scala.util.Success

object StringObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = StringObj[~]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Font)
  val prefix        : String   = "String"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = StringObj
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: StringObj[S])(implicit tx: S#Tx): ObjListView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case StringObj.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[S](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
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

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    object p extends ObjViewCmdLineParser[Config[S]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[String]  = trailArg(descr = "Initial string value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
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
                                override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends ObjListView[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.SimpleExpr[S, String, StringObj]
      with ObjListViewImpl.StringRenderer {

    type Repr = StringObj[S]

    def factory: ObjView.Factory = StringObjView

    def exprType: Type.Expr[String, StringObj] = StringObj

    def convertEditValue(v: Any): Option[String] = Some(v.toString)

    def expr(implicit tx: S#Tx): StringObj[S] = objH()
  }
}
