/*
 *  IntVectorObjView.scala
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
import de.sciss.kollflitz.Vec
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Expr, IntVector, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.{MessageException, ObjListView, ObjView, Shapes}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Confluent, Universe}

import javax.swing.Icon
import scala.swing.TextField
import scala.util.{Failure, Try}

object IntVectorObjView extends ObjListView.Factory {
  type E[T <: LTxn[T]] = IntVector[T]
  val icon          : Icon      = raphaelIcon(Shapes.IntegerNumberVector)
  val prefix        : String   = "IntVector"
  def humanName     : String   = "Int Vector"
  def tpe           : Obj.Type  = IntVector
  def category      : String   = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: IntVector[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case IntVector.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new Impl[T](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: Vec[Int], const: Boolean = false)

  private def parseString(s: String): Try[Vec[Int]] =
    Try(s.split(",").iterator.map(x => x.trim().toInt).toIndexedSeq)
      .recoverWith { case _ => Failure(MessageException(s"Cannot parse '$s' as $humanName")) }

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val ggValue = new TextField("0,0")
    val res0 = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = parseString(ggValue.text))
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean]   = opt   (descr = s"Make constant instead of variable")
      val value: Opt[Vec[Int]]  = vecArg(descr = "Comma-separated list of int values (, for empty list)")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = IntVector.newConst[T](value)
    val obj   = if (const) obj0 else IntVector.newVar(obj0)
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, IntVector[T]], value0: Vec[Int],
                                override val isListCellEditable: Boolean, val isViewable: Boolean)
    extends ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.VectorExpr[T, Int, IntVector] {

    type Repr = IntVector[T]

    def factory: ObjView.Factory = IntVectorObjView

    def exprType: Expr.Type[Vec[Int], IntVector] = IntVector

    def expr(implicit tx: T): IntVector[T] = objH()

    protected var exprValue: Vec[Int] = value0

    def value: String =
      exprValue.mkString(",")

    def convertEditValue(v: Any): Option[Vec[Int]] = v match {
      case num: Vec[Any] =>
        num.foldLeft(Option(Vec.empty[Int])) {
          case (Some(prev), d: Int) => Some(prev :+ d)
          case _                    => None
        }

      case s: String  => parseString(s).toOption
      case _          => None
    }
  }
}
