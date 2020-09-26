/*
 *  DoubleObjView.scala
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
import de.sciss.lucre.{DoubleObj, Expr, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.impl.{ObjGraphemeViewImpl, ObjViewCmdLineParser}
import de.sciss.mellite.{GraphemeRendering, GraphemeView, Insets, ObjGraphemeView, ObjListView, ObjView, Shapes}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.{Confluent, Universe}
import de.sciss.synth.proc.Implicits._
import javax.swing.{Icon, SpinnerNumberModel}

import scala.swing.{Component, Graphics2D, Label}
import scala.util.{Success, Try}

object DoubleObjView extends ObjListView.Factory with ObjGraphemeView.Factory {
  type E[T <: LTxn[T]]          = DoubleObj[T]
  type V                        = Double
  val icon          : Icon      = raphaelIcon(Shapes.RealNumber)
  val prefix        : String    = "Double"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = DoubleObj
  def category      : String    = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: E[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleObj.Var(_) => true
      case _                => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl[T](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: Double, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val model   = new SpinnerNumberModel(0.0, Double.NegativeInfinity, Double.PositiveInfinity, 1.0)
    val ggValue = new Spinner(model)
    val res0    = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Success(model.getNumber.doubleValue))
    val res     = res0.map { c => Config[T](name = c.name, value = c.value) }
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
      val const : Opt[Boolean]  = opt     (descr = s"Make constant instead of variable")
      val value : Opt[Double]   = trailArg(descr = "Initial double value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = DoubleObj.newConst[T](value)
    val obj   = if (const) obj0 else DoubleObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  def mkGraphemeView[T <: Txn[T]](entry: Entry[T], obj: E[T], mode: GraphemeView.Mode)
                                 (implicit tx: T): ObjGraphemeView[T] = {
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    // assert (entry.value == value)
    new GraphemeImpl[T](tx.newHandle(entry), tx.newHandle(obj), value = obj.value, isViewable = isViewable)
      .init(obj, entry)
  }

  // ---- basic ----

  private abstract class Impl[T <: Txn[T]](val objH: Source[T, E[T]], val isViewable: Boolean)
    extends ObjViewImpl.Impl[T]
    with ObjViewImpl.ExprLike[T, V, E] {

    type Repr = DoubleObj[T]

    final def factory: ObjView.Factory = DoubleObjView

    final def exprType: Expr.Type[V, E] = DoubleObj

    final def expr(implicit tx: T): E[T] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[T <: Txn[T]](objH: Source[T, E[T]], var value: Double,
                                            override val isListCellEditable: Boolean, isViewable: Boolean)
    extends Impl(objH, isViewable = isViewable) with ObjListView[T]
      with ObjListViewImpl.SimpleExpr[T, V, E] {

    def configureListCellRenderer(label: Label): Component = {
      label.text = value.toFloat.toString   // avoid excessive number of digits!
      label
    }

    def convertEditValue(v: Any): Option[Double] = v match {
      case num: V       => Some(num)
      case s  : String  => Try(s.toDouble).toOption
    }
  }

  // ---- GraphemeObjView ----

  def graphemePaintFront[T <: Txn[T]](view: ObjGraphemeView[T], value: Double, g: Graphics2D,
                                      gv: GraphemeView[T], r: GraphemeRendering): Unit = {
    import ObjGraphemeView.{HandleDiameter, HandleRadius}
    val c         = gv.canvas
    val selected  = gv.selectionModel.contains(view)
    val time0     = view.timeValue
    val time1     = if (selected) time0 + r.ttMoveState.deltaTime    else time0
    val value1    = if (selected) value + r.ttMoveState.deltaModelY  else value
    val x         = c.frameToScreen   (time1  )
    val y         = c.modelPosToScreen(value1 )
    val p         = r.ellipse1
    p.setFrame(x - 2, y - 2, 4, 4)
    g.setPaint(if (selected) r.pntRegionBackgroundSelected else r.pntRegionBackground)
    g.fill(p)
    p.setFrame(x - HandleRadius, y - HandleRadius, HandleDiameter, HandleDiameter)
    g.setPaint(if (selected) r.pntRegionOutlineSelected else r.pntRegionOutline)
    g.draw(p)
  }

  private final class GraphemeImpl[T <: Txn[T]](val entryH: Source[T, Entry[T]],
                                                objH: Source[T, E[T]],
                                                var value: V,
                                                isViewable: Boolean)
    extends Impl[T](objH, isViewable = isViewable)
    with ObjGraphemeViewImpl.SimpleExpr[T, V, E]
    with ObjGraphemeView.HasStartLevels[T] {

    def insets: Insets = ObjGraphemeView.DefaultInsets

    def startLevels: Vec[Double] = value +: Vector.empty

    override def paintFront(g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit =
      graphemePaintFront(this, value, g, gv, r)
  }
}
