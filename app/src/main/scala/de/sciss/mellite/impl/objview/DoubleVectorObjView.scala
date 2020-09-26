/*
 *  DoubleVectorObjView.scala
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

import java.awt.geom.Area

import de.sciss.desktop
import de.sciss.kollflitz.Vec
import de.sciss.lucre.{DoubleVector, Expr, Obj, Source, Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{GraphemeRendering, GraphemeView, Insets, MessageException, ObjGraphemeView, ObjListView, ObjView}
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.Shapes
import de.sciss.mellite.impl.{ObjGraphemeViewImpl, ObjViewCmdLineParser}
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.Icon

import scala.swing.{Component, Graphics2D, Label, TextField}
import scala.util.{Failure, Try}

object DoubleVectorObjView extends ObjListView.Factory with ObjGraphemeView.Factory {
  type E[T <: LTxn[T]]       = DoubleVector[T]
  type V                        = Vec[Double]
  val icon          : Icon      = raphaelIcon(Shapes.RealNumberVector)
  val prefix        : String    = "DoubleVector"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = DoubleVector
  def category      : String    = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: E[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleVector.Var(_)  => true
      case _            => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl[T](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: V, const: Boolean = false)

  private def parseString(s: String): Try[V] =
    Try(s.split(",").iterator.map(x => x.trim().toDouble).toIndexedSeq)
      .recoverWith { case _ => Failure(MessageException(s"Cannot parse '$s' as $humanName")) }

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val ggValue = new TextField("0.0,0.0")
    val res0 = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = parseString(ggValue.text))
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean]     = opt   (descr = s"Make constant instead of variable")
      val value: Opt[Vec[Double]] = vecArg(descr = "Comma-separated list of double values (, for empty list)")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = DoubleVector.newConst[T](value)
    val obj   = if (const) obj0 else DoubleVector.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  def mkGraphemeView[T <: Txn[T]](entry: Entry[T], obj: E[T], mode: GraphemeView.Mode)
                                 (implicit tx: T): ObjGraphemeView[T] = {
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new GraphemeImpl[T](tx.newHandle(entry), tx.newHandle(obj), value = obj.value, isViewable = isViewable)
      .init(obj, entry)
  }

  // ---- basic ----

  private abstract class Impl[T <: Txn[T]](val objH: Source[T, E[T]], val isViewable: Boolean)
    extends ObjViewImpl.Impl[T]
      with ObjViewImpl.ExprLike[T, V, E] {

    type Repr = DoubleVector[T]

    final def factory: ObjView.Factory = DoubleVectorObjView

    final def exprType: Expr.Type[V, E] = DoubleVector

    final def expr(implicit tx: T): E[T] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[T <: Txn[T]](objH: Source[T, E[T]], var value: V,
                                            override val isListCellEditable: Boolean, isViewable: Boolean)
    extends Impl(objH, isViewable = isViewable) with ObjListView[T]
      with ObjListViewImpl.SimpleExpr[T, V, E] {

    def convertEditValue(v: Any): Option[V] = v match {
      case num: Vec[_] => num.foldLeft(Option(Vector.empty[Double])) {
        case (Some(prev), d: Double) => Some(prev :+ d)
        case _ => None
      }
      case s: String  => parseString(s).toOption
    }

    def configureListCellRenderer(label: Label): Component = {
      label.text = value.iterator.map(_.toFloat).mkString(",")  // avoid excessive number of digits!
      label
    }
  }

  // ---- GraphemeObjView ----

  private final class GraphemeImpl[T <: Txn[T]](val entryH: Source[T, Entry[T]],
                                                objH: Source[T, E[T]],
                                                var value: V,
                                                isViewable: Boolean)
    extends Impl[T](objH, isViewable = isViewable)
      with ObjGraphemeViewImpl.SimpleExpr[T, V, E]
      with ObjGraphemeView.HasStartLevels[T] {

    private[this] val allSame = value.size <= 1 || { val v0 = value.head; value.forall(_ == v0) }

    def insets: Insets = ObjGraphemeView.DefaultInsets

    def startLevels: Vec[Double] = value

    override def paintFront(g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit = {
      import ObjGraphemeView.{HandleDiameter, HandleRadius}

      if (value.isEmpty) return
      if (allSame) {
        DoubleObjView.graphemePaintFront(this, value.head, g, gv, r)
        return
      }

      val c   = gv.canvas
      val jc  = c.canvasComponent.peer
      val h   = jc.getHeight
      val x   = c.frameToScreen(timeValue)

      val a1  = r.area1
      val a2  = r.area2
      val p   = r.ellipse1 // r.shape1
      a1.reset()
      a2.reset()
      val hm  = h - 1
      var i   = 0
      var min = Double.MaxValue
      var max = Double.MinValue
      while (i < value.size) {
        val v = value(i)
        i += 1
        val y = v * hm
        p.setFrame(x - 2, y - 2, 4, 4)
        a1.add(new Area(p))
        p.setFrame(x - HandleRadius, y - HandleRadius, HandleDiameter, HandleDiameter)
        a2.add(new Area(p))
        if (y < min) min = y
        if (y > max) max = y
      }

      g.setStroke(r.strokeInletSpan)
      g.setPaint(r.pntInletSpan)
      val ln = r.shape1
      ln.reset()
      ln.moveTo(x, min)
      ln.lineTo(x, max)
      g.draw(ln)
      g.setStroke(r.strokeNormal)
      val selected = gv.selectionModel.contains(this)
      g.setPaint(if (selected) r.pntRegionBackgroundSelected else r.pntRegionBackground)
      g.fill(a1)
      g.setPaint(if (selected) r.pntRegionOutlineSelected else r.pntRegionOutline)
      g.draw(a2)
    }
  }
}
