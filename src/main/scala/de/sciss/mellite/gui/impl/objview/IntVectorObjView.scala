/*
 *  IntVectorObjView.scala
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
import de.sciss.kollflitz.Vec
import de.sciss.lucre.expr.{IntVector, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.gui.{ListObjView, MessageException, ObjView, Shapes}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.{Component, Label, TextField}
import scala.util.{Failure, Try}

object IntVectorObjView extends ListObjView.Factory {
  type E[S <: stm.Sys[S]] = IntVector[S]
  val icon          : Icon      = raphaelIcon(Shapes.IntegerNumberVector)
  val prefix        : String   = "IntVector"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = IntVector
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: IntVector[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case IntVector.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Vec[Int], const: Boolean = false)

  private def parseString(s: String): Try[Vec[Int]] =
    Try(s.split(",").iterator.map(x => x.trim().toInt).toIndexedSeq)
      .recoverWith { case _ => Failure(MessageException(s"Cannot parse '$s' as $humanName")) }

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue = new TextField("0,0")
    val res0 = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = parseString(ggValue.text))
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = Config(value = Vec())
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[Seq[Int]]("values")
      .text("Comma-separated list of int values (, for empty list)")
      .required()
      .action((v, c) => c.copy(value = v.toIndexedSeq))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = IntVector.newConst[S](value)
    val obj   = if (const) obj0 else IntVector.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, IntVector[S]], var value: Vec[Int],
                                override val isEditable: Boolean, val isViewable: Boolean)
    extends ListObjView[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, Vec[Int], IntVector] {

    type E[~ <: stm.Sys[~]] = IntVector[~]

    def factory: ObjView.Factory = IntVectorObjView

    def exprType: Type.Expr[Vec[Int], IntVector] = IntVector

    def expr(implicit tx: S#Tx): IntVector[S] = objH()

    def convertEditValue(v: Any): Option[Vec[Int]] = v match {
      case num: Vec[_] => num.foldLeft(Option(Vec.empty[Int])) {
        case (Some(prev), d: Int) => Some(prev :+ d)
        case _ => None
      }
      case s: String  => parseString(s).toOption
    }

    def configureRenderer(label: Label): Component = {
      label.text = value.mkString(",")
      label
    }
  }
}
