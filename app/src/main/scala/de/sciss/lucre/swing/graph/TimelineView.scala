/*
 *  TimelineView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.lucre.swing.graph

import de.sciss.lucre.expr.ExElem.{ProductReader, RefMapIn}
import de.sciss.lucre.expr.graph.impl.MappedIExpr
import de.sciss.lucre.expr.graph.{Ex, Obj, Timed, Timeline => _Timeline}
import de.sciss.lucre.expr.{Context, ExElem}
import de.sciss.lucre.{Adjunct, IExpr, ITargets, Txn}
import de.sciss.mellite
import de.sciss.mellite.{DragAndDrop, TimelineView => _TimelineView}
import de.sciss.proc.TimeRef
import de.sciss.serial.DataInput
import de.sciss.span.Span.SpanOrVoid
import de.sciss.span.{Span, SpanLike}

import java.awt.datatransfer.Transferable

object TimelineView {
  private lazy val _init: Unit = {
    Adjunct.addFactory(Drop)
    ExElem.addProductReaderSq(Seq(
      SampleRate, Position, Selection, Bounds, Visible, SelectedObjects, Timeline
    ))
  }

  def init(): Unit = _init

  private[lucre] object Empty extends TimelineView {
    override def toString = "TimelineView.Empty"

//    private[this] val emptySpan = Span(0L, 0L)

    def sampleRate: Double      = TimeRef.SampleRate
    def visible   : SpanOrVoid  = Span.Void
    def position  : Long        = 0L
    def selection : SpanOrVoid  = Span.Void
    def bounds    : SpanLike    = Span.Void
//    def virtual   : Span        = emptySpan // good?
    def timeline  : _Timeline   = _Timeline.Empty
//    def edit      : Edit        = Edit.Empty

    def selectedObjects: Seq[Timed[Obj]] = Nil
  }

  implicit object Drop extends DropTarget.Selector[TimelineView] with Adjunct.Factory {
    final val id = 4000

    override def readIdentifiedAdjunct(in: DataInput): Adjunct = this

    def defaultData: TimelineView = Empty

    def canImport[T <: Txn[T]](t: Transferable)(implicit ctx: Context[T]): Boolean =
      t.isDataFlavorSupported(_TimelineView.Flavor)

    def importData[T <: Txn[T]](t: Transferable)(implicit ctx: Context[T]): TimelineView = {
      val tv              = DragAndDrop.getTransferData(t, _TimelineView.Flavor).view
      val tlm             = tv.timelineModel
      val sameWorkspace   = tv.universe.workspace == ctx.workspace
      val (selectedObjects, timeline /*, edit*/) = if (!sameWorkspace) (Nil, _Timeline.Empty /*, Edit.Empty*/) else {
        val tvc     = tv.asInstanceOf[mellite.TimelineView[T]]
        val system  = tvc.universe.workspace.system
        val _sel    = tvc.selectionModel.iterator.map { view =>
          val span = view.spanValue
          val value = Obj.wrapH(view.objH, system)
          Timed(span, value)
        } .toIndexedSeq

        (_sel, _Timeline.wrapH(tvc.objH, system) /* , huh */)
      }

      // println(s"sameWorkspace = $sameWorkspace; has sel? ${tv.selectionModel.nonEmpty} ; ${selectedObjects.size}")

      new Impl(
        sampleRate      = tlm.sampleRate,
        position        = tlm.position,
        selection       = tlm.selection,
        bounds          = tlm.bounds,
        visible         = tlm.visible,
        virtual         = tlm.virtual,
        selectedObjects = selectedObjects,
        timeline        = timeline
      )
    }
  }

  private final class SampleRateExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, Double](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): Double = t.sampleRate
  }

  object SampleRate extends ProductReader[SampleRate] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): SampleRate = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new SampleRate(_in)
    }
  }
  final case class SampleRate(in: Ex[TimelineView]) extends Ex[Double] {
    type Repr[T <: Txn[T]] = IExpr[T, Double]

    override def productPrefix = s"TimelineView$$SampleRate"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.targets
      new SampleRateExpanded(in.expand[T], tx)
    }
  }

  private final class PositionExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, Long](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): Long = t.position
  }

  object Position extends ProductReader[Position] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Position = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new Position(_in)
    }
  }
  final case class Position(in: Ex[TimelineView]) extends Ex[Long] {
    type Repr[T <: Txn[T]] = IExpr[T, Long]

    override def productPrefix = s"TimelineView$$Position"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.targets
      new PositionExpanded(in.expand[T], tx)
    }
  }

  private final class SelectionExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, SpanOrVoid](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): SpanOrVoid = t.selection
  }

  object Selection extends ProductReader[Selection] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Selection = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new Selection(_in)
    }
  }
  final case class Selection(in: Ex[TimelineView]) extends Ex[SpanOrVoid] {
    type Repr[T <: Txn[T]] = IExpr[T, SpanOrVoid]

    override def productPrefix = s"TimelineView$$Selection"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.targets
      new SelectionExpanded(in.expand[T], tx)
    }
  }

  private final class BoundsExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, SpanLike](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): SpanLike = t.bounds
  }

  object Bounds extends ProductReader[Bounds] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Bounds = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new Bounds(_in)
    }
  }
  final case class Bounds(in: Ex[TimelineView]) extends Ex[SpanLike] {
    type Repr[T <: Txn[T]] = IExpr[T, SpanLike]

    override def productPrefix = s"TimelineView$$Bounds"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.targets
      new BoundsExpanded(in.expand[T], tx)
    }
  }

  private final class VisibleExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, SpanOrVoid](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): SpanOrVoid = t.visible
  }

  object Visible extends ProductReader[Visible] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Visible = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new Visible(_in)
    }
  }
  final case class Visible(in: Ex[TimelineView]) extends Ex[SpanOrVoid] {
    type Repr[T <: Txn[T]] = IExpr[T, SpanOrVoid]

    override def productPrefix = s"TimelineView$$Visible"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.targets
      new VisibleExpanded(in.expand[T], tx)
    }
  }

  private final class SelectedObjectsExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, Seq[Timed[Obj]]](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): Seq[Timed[Obj]] = t.selectedObjects
  }

  object SelectedObjects extends ProductReader[SelectedObjects] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): SelectedObjects = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new SelectedObjects(_in)
    }
  }
  final case class SelectedObjects(in: Ex[TimelineView]) extends Ex[Seq[Timed[Obj]]] {
    type Repr[T <: Txn[T]] = IExpr[T, Seq[Timed[Obj]]]

    override def productPrefix = s"TimelineView$$SelectedObjects"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): IExpr[T, Seq[Timed[Obj]]] = {
      import ctx.targets
      new SelectedObjectsExpanded(in.expand[T], tx)
    }
  }

  private final class TimelineExpanded[T <: Txn[T]](in: IExpr[T, TimelineView], tx0: T)(implicit targets: ITargets[T])
    extends MappedIExpr[T, TimelineView, _Timeline](in, tx0) {

    protected def mapValue(t: TimelineView)(implicit tx: T): _Timeline = t.timeline
  }

  object Timeline extends ProductReader[Timeline] {
    override def read(in: RefMapIn, key: String, arity: Int, adj: Int): Timeline = {
      require (arity == 1 && adj == 0)
      val _in = in.readEx[TimelineView]()
      new Timeline(_in)
    }
  }
  final case class Timeline(in: Ex[TimelineView]) extends Ex[_Timeline] {
    type Repr[T <: Txn[T]] = IExpr[T, _Timeline]

    override def productPrefix = s"TimelineView$$Timeline"  // serialization

    protected def mkRepr[T <: Txn[T]](implicit ctx: Context[T], tx: T): Repr[T] = {
      import ctx.targets
      new TimelineExpanded(in.expand[T], tx)
    }
  }

  implicit class Ops(private val t: Ex[TimelineView]) extends AnyVal {
    def sampleRate      : Ex[Double     ]     = SampleRate      (t)
    def position        : Ex[Long       ]     = Position        (t)
    def selection       : Ex[SpanOrVoid ]     = Selection       (t)
    def bounds          : Ex[SpanLike   ]     = Bounds          (t)
    def visible         : Ex[SpanOrVoid ]     = Visible         (t)
//  def virtual         : Ex[Span       ]     = Virtual         (t)
    def selectedObjects : Ex[Seq[Timed[Obj]]] = SelectedObjects (t)
    def timeline        : Ex[_Timeline  ]     = Timeline        (t)
  }

  private final class Impl(
    val sampleRate      : Double,
    val position        : Long,
    val selection       : SpanOrVoid,
    val bounds          : SpanLike,
    val visible         : Span,
    val virtual         : Span,
    val selectedObjects : Seq[Timed[Obj]],
    val timeline        : _Timeline
//    val edit            : Edit

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

  def timeline  : _Timeline

  def selectedObjects: Seq[Timed[Obj]]

//  def edit      : Edit
}
