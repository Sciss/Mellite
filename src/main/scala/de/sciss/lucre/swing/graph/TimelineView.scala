/*
 *  TimelineView.scala
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

package de.sciss.lucre.swing.graph

import java.awt.datatransfer.Transferable

import de.sciss.lucre.aux.Aux
import de.sciss.lucre.event.ITargets
import de.sciss.lucre.expr.graph.impl.{MappedIExpr, ObjImpl}
import de.sciss.lucre.expr.graph.{Ex, Obj, Timed}
import de.sciss.lucre.expr.{Context, IExpr}
import de.sciss.lucre.stm.Sys
import de.sciss.mellite.gui.{DragAndDrop, TimelineView => _TimelineView}
import de.sciss.serial.DataInput
import de.sciss.span.Span.SpanOrVoid
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc.TimeRef

object TimelineView {
  private lazy val _init: Unit =
    Aux.addFactory(Drop)

  def init(): Unit = _init

  object Empty extends TimelineView {
    override def toString = "TimelineView.Empty"

//    private[this] val emptySpan = Span(0L, 0L)

    def sampleRate: Double      = TimeRef.SampleRate
    def visible   : SpanOrVoid  = Span.Void
    def position  : Long        = 0L
    def selection : SpanOrVoid  = Span.Void
    def bounds    : SpanLike    = Span.Void
//    def virtual   : Span        = emptySpan // good?

    def selectedObjects: Seq[Timed[Obj]] = Nil

//    Seq(
//      Timed(Span(2, 3), new Obj {}),
//      Timed(Span(3, 4), new Obj {})
//    )
  }

  implicit object Drop extends DropTarget.Selector[TimelineView] with Aux.Factory {
    final val id = 4000

    def readIdentifiedAux(in: DataInput): Aux = this

    def defaultData: TimelineView = Empty

    def canImport[S <: Sys[S]](t: Transferable)(implicit ctx: Context[S]): Boolean =
      t.isDataFlavorSupported(_TimelineView.Flavor)

    def importData[S <: Sys[S]](t: Transferable)(implicit ctx: Context[S]): TimelineView = {
      val tv              = DragAndDrop.getTransferData(t, _TimelineView.Flavor).view
      val tlm             = tv.timelineModel
      val sameWorkspace   = tv.universe.workspace == ctx.workspace
      val selectedObjects = if (!sameWorkspace) Nil else {
        val tvc     = tv.asInstanceOf[_TimelineView[S]]
        val system  = tvc.universe.workspace.system
        tvc.selectionModel.iterator.map { view =>
          val span = view.spanValue
          val value: Obj = new ObjImpl(view.objH, system)
          Timed(span, value)
        } .toIndexedSeq
      }

      // println(s"sameWorkspace = $sameWorkspace; has sel? ${tv.selectionModel.nonEmpty} ; ${selectedObjects.size}")

      new Impl(
        sampleRate      = tlm.sampleRate,
        position        = tlm.position,
        selection       = tlm.selection,
        bounds          = tlm.bounds,
        visible         = tlm.visible,
        virtual         = tlm.virtual,
        selectedObjects = selectedObjects
      )
    }
  }

  private final class SampleRateExpanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
    extends MappedIExpr[S, TimelineView, Double](in, tx0) {

    protected def mapValue(t: TimelineView): Double = t.sampleRate
  }

  final case class SampleRate(in: Ex[TimelineView]) extends Ex[Double] {
    type Repr[S <: Sys[S]] = IExpr[S, Double]

    override def productPrefix = s"TimelineView$$SampleRate"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new SampleRateExpanded(in.expand[S], tx)
    }
  }

  object Position {
    def apply(t: Ex[TimelineView]): Position = Impl(t)

    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
      extends MappedIExpr[S, TimelineView, Long](in, tx0) {

      protected def mapValue(t: TimelineView): Long = t.position
    }

    private final case class Impl(in: Ex[TimelineView]) extends Position {
      type Repr[S <: Sys[S]] = IExpr[S, Long]

      override def productPrefix = s"TimelineView$$Position"  // serialization

      protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
        import ctx.targets
        new Expanded(in.expand[S], tx)
      }
    }
  }
  trait Position extends Ex[Long]

  private final class SelectionExpanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
    extends MappedIExpr[S, TimelineView, SpanOrVoid](in, tx0) {

    protected def mapValue(t: TimelineView): SpanOrVoid = t.selection
  }

  final case class Selection(in: Ex[TimelineView]) extends Ex[SpanOrVoid] {
    type Repr[S <: Sys[S]] = IExpr[S, SpanOrVoid]

    override def productPrefix = s"TimelineView$$Selection"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new SelectionExpanded(in.expand[S], tx)
    }
  }

  private final class BoundsExpanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
    extends MappedIExpr[S, TimelineView, SpanLike](in, tx0) {

    protected def mapValue(t: TimelineView): SpanLike = t.bounds
  }

  final case class Bounds(in: Ex[TimelineView]) extends Ex[SpanLike] {
    type Repr[S <: Sys[S]] = IExpr[S, SpanLike]

    override def productPrefix = s"TimelineView$$Bounds"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new BoundsExpanded(in.expand[S], tx)
    }
  }

  private final class VisibleExpanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
    extends MappedIExpr[S, TimelineView, SpanOrVoid](in, tx0) {

    protected def mapValue(t: TimelineView): SpanOrVoid = t.visible
  }

  final case class Visible(in: Ex[TimelineView]) extends Ex[SpanOrVoid] {
    type Repr[S <: Sys[S]] = IExpr[S, SpanOrVoid]

    override def productPrefix = s"TimelineView$$Visible"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new VisibleExpanded(in.expand[S], tx)
    }
  }


  private final class SelectedObjectsExpanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
    extends MappedIExpr[S, TimelineView, Seq[Timed[Obj]]](in, tx0) {

    protected def mapValue(t: TimelineView): Seq[Timed[Obj]] = t.selectedObjects
  }

  final case class SelectedObjects(in: Ex[TimelineView]) extends Ex[Seq[Timed[Obj]]] {
    type Repr[S <: Sys[S]] = IExpr[S, Seq[Timed[Obj]]]

    override def productPrefix = s"TimelineView$$SelectedObjects"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): IExpr[S, Seq[Timed[Obj]]] = {
      import ctx.targets
      new SelectedObjectsExpanded(in.expand[S], tx)
    }
  }

  implicit class Ops[S <: Sys[S]](private val t: Ex[TimelineView]) extends AnyVal {
    def sampleRate: Ex[Double     ] = SampleRate(t)
    def position  : Ex[Long       ] = Position  (t)
    def selection : Ex[SpanOrVoid ] = Selection (t)
    def bounds    : Ex[SpanLike   ] = Bounds    (t)
    def visible   : Ex[SpanOrVoid ] = Visible   (t)
//    def virtual   : Ex[Span       ] = Virtual   (t)

    def selectedObjects: Ex[Seq[Timed[Obj]]] = SelectedObjects(t)
  }

  private final class Impl(
    val sampleRate      : Double,
    val position        : Long,
    val selection       : SpanOrVoid,
    val bounds          : SpanLike,
    val visible         : Span,
    val virtual         : Span,

    val selectedObjects : Seq[Timed[Obj]]

  ) extends TimelineView {

    override def toString: String =
      s"TimelineView(sampleRate = $sampleRate, position = $position, selection = $selection, " +
        s"bounds = $bounds, visible = $visible, virtual = $virtual)"
  }
}
trait TimelineView {
  def sampleRate: Double
  def position  : Long
  def selection : SpanOrVoid
  def bounds    : SpanLike

  // N.B. `t.visible` is `Span`, but we use the more general
  // `SpanOrVoid` to have a better match for `TimelineView.Empty`
  def visible   : SpanOrVoid

  //  def virtual   : Span

  def selectedObjects: Seq[Timed[Obj]]
}
