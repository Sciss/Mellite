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
import de.sciss.lucre.expr.{DoubleObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{GraphemeRendering, GraphemeView, Insets, ObjGraphemeView, ObjListView, ObjView}
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.Shapes
import de.sciss.mellite.impl.{ObjGraphemeViewImpl, ObjViewCmdLineParser}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Confluent, Universe}
import javax.swing.{Icon, SpinnerNumberModel}

import scala.swing.{Component, Graphics2D, Label}
import scala.util.{Success, Try}

object DoubleObjView extends ObjListView.Factory with ObjGraphemeView.Factory {
  type E[S <: stm.Sys[S]]       = DoubleObj[S]
  type V                        = Double
  val icon          : Icon      = raphaelIcon(Shapes.RealNumber)
  val prefix        : String    = "Double"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = DoubleObj
  def category      : String    = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: E[S])(implicit tx: S#Tx): ObjListView[S] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case DoubleObj.Var(_) => true
      case _                => false
    }
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    new ListImpl[S](tx.newHandle(obj), value, isListCellEditable = isEditable, isViewable = isViewable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Double, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val model   = new SpinnerNumberModel(0.0, Double.NegativeInfinity, Double.PositiveInfinity, 1.0)
    val ggValue = new Spinner(model)
    val res0    = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Success(model.getNumber.doubleValue))
    val res     = res0.map { c => Config[S](name = c.name, value = c.value) }
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
      val const : Opt[Boolean]  = opt     (descr = s"Make constant instead of variable")
      val value : Opt[Double]   = trailArg(descr = "Initial double value")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = DoubleObj.newConst[S](value)
    val obj   = if (const) obj0 else DoubleObj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  def mkGraphemeView[S <: Sys[S]](entry: Entry[S], obj: E[S], mode: GraphemeView.Mode)
                                 (implicit tx: S#Tx): ObjGraphemeView[S] = {
    val isViewable  = tx.isInstanceOf[Confluent.Txn]
    // assert (entry.value == value)
    new GraphemeImpl[S](tx.newHandle(entry), tx.newHandle(obj), value = obj.value, isViewable = isViewable)
      .init(obj, entry)
  }

  // ---- basic ----

  private abstract class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, E[S]], val isViewable: Boolean)
    extends ObjViewImpl.Impl[S]
    with ObjViewImpl.ExprLike[S, V, E] {

    type Repr = DoubleObj[S]

    final def factory: ObjView.Factory = DoubleObjView

    final def exprType: Type.Expr[V, E] = DoubleObj

    final def expr(implicit tx: S#Tx): E[S] = objH()
  }

  // ---- ListObjView ----

  private final class ListImpl[S <: Sys[S]](objH: stm.Source[S#Tx, E[S]], var value: Double,
                                            override val isListCellEditable: Boolean, isViewable: Boolean)
    extends Impl(objH, isViewable = isViewable) with ObjListView[S]
      with ObjListViewImpl.SimpleExpr[S, V, E] {

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

  def graphemePaintFront[S <: Sys[S]](view: ObjGraphemeView[S], value: Double, g: Graphics2D,
                                      gv: GraphemeView[S], r: GraphemeRendering): Unit = {
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

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, E[S]],
                                                var value: V,
                                                isViewable: Boolean)
    extends Impl[S](objH, isViewable = isViewable)
    with ObjGraphemeViewImpl.SimpleExpr[S, V, E]
    with ObjGraphemeView.HasStartLevels[S] {

    def insets: Insets = ObjGraphemeView.DefaultInsets

    def startLevels: Vec[Double] = value +: Vector.empty

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit =
      graphemePaintFront(this, value, g, gv, r)
  }
}
