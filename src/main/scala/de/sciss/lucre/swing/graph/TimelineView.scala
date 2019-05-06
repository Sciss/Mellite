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
import de.sciss.lucre.event.impl.IEventImpl
import de.sciss.lucre.event.{IEvent, IPull, ITargets}
import de.sciss.lucre.expr.{Ex, IExpr}
import de.sciss.lucre.stm.Sys
import de.sciss.mellite.gui.{DragAndDrop, TimelineView => _TimelineView}
import de.sciss.model.Change
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
  }

  implicit object Drop extends DropTarget.Selector[TimelineView] with Aux.Factory {
    final val id = 4000

    def readIdentifiedAux(in: DataInput): Aux = this

    def defaultData: TimelineView = Empty

    def canImport(t: Transferable): Boolean =
      t.isDataFlavorSupported(_TimelineView.Flavor)

    def importData(t: Transferable): TimelineView = {
      val tv: _TimelineView[_] = DragAndDrop.getTransferData(t, _TimelineView.Flavor).view
      val tlm = tv.timelineModel
      new Impl(
        sampleRate = tlm.sampleRate,
        position   = tlm.position,
        selection  = tlm.selection,
        bounds     = tlm.bounds,
        visible    = tlm.visible,
        virtual    = tlm.virtual
      )
    }
  }

  private abstract class ExMap[S <: Sys[S], A](in: IExpr[S, TimelineView], tx0: S#Tx)
                                              (implicit protected val targets: ITargets[S])
    extends IExpr[S, A] with IEventImpl[S, Change[A]] {

    in.changed.--->(this)(tx0)

    protected def map(t: TimelineView): A

    def value(implicit tx: S#Tx): A = map(in.value)

    private[lucre] def pullUpdate(pull: IPull[S])(implicit tx: S#Tx): Option[Change[A]] =
      pull(in.changed).flatMap { ch =>
        val before  = map(ch.before )
        val now     = map(ch.now    )
        if (before == now) None else Some(Change(before, now))
      }

    def dispose()(implicit tx: S#Tx): Unit =
      in.changed.-/->(this)

    def changed: IEvent[S, Change[A]] = this
  }

  object SampleRate {
    def apply(t: Ex[TimelineView]): SampleRate = Impl(t)

    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
      extends ExMap[S, Double](in, tx0) {

      protected def map(t: TimelineView): Double = t.sampleRate
    }

    private final case class Impl(in: Ex[TimelineView]) extends SampleRate {
      override def productPrefix = s"TimelineView$$SampleRate"  // serialization

      def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Double] = {
        import ctx.targets
        new Expanded(in.expand[S], tx)
      }
    }
  }
  trait SampleRate extends Ex[Double]

  object Position {
    def apply(t: Ex[TimelineView]): Position = Impl(t)

    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
      extends ExMap[S, Long](in, tx0) {

      protected def map(t: TimelineView): Long = t.position
    }

    private final case class Impl(in: Ex[TimelineView]) extends Position {
      override def productPrefix = s"TimelineView$$Position"  // serialization

      def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Long] = {
        import ctx.targets
        new Expanded(in.expand[S], tx)
      }
    }
  }
  trait Position extends Ex[Long]

  object Selection {
    def apply(t: Ex[TimelineView]): Selection = Impl(t)

    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
      extends ExMap[S, SpanOrVoid](in, tx0) {

      protected def map(t: TimelineView): SpanOrVoid = t.selection
    }

    private final case class Impl(in: Ex[TimelineView]) extends Selection {
      override def productPrefix = s"TimelineView$$Selection"  // serialization

      def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, SpanOrVoid] = {
        import ctx.targets
        new Expanded(in.expand[S], tx)
      }
    }
  }
  trait Selection extends Ex[SpanOrVoid]

  object Bounds {
    def apply(t: Ex[TimelineView]): Bounds = Impl(t)

    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
      extends ExMap[S, SpanLike](in, tx0) {

      protected def map(t: TimelineView): SpanLike = t.bounds
    }

    private final case class Impl(in: Ex[TimelineView]) extends Bounds {
      override def productPrefix = s"TimelineView$$Bounds"  // serialization

      def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, SpanLike] = {
        import ctx.targets
        new Expanded(in.expand[S], tx)
      }
    }
  }
  trait Bounds extends Ex[SpanLike]

  object Visible {
    def apply(t: Ex[TimelineView]): Visible = Impl(t)

    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
      extends ExMap[S, SpanOrVoid](in, tx0) {

      protected def map(t: TimelineView): SpanOrVoid = t.visible
    }

    private final case class Impl(in: Ex[TimelineView]) extends Visible {
      override def productPrefix = s"TimelineView$$Visible"  // serialization

      def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, SpanOrVoid] = {
        import ctx.targets
        new Expanded(in.expand[S], tx)
      }
    }
  }
  trait Visible extends Ex[SpanOrVoid]

//  object Virtual {
//    def apply(t: Ex[TimelineView]): Virtual = Impl(t)
//
//    private final class Expanded[S <: Sys[S]](in: IExpr[S, TimelineView], tx0: S#Tx)(implicit targets: ITargets[S])
//      extends ExMap[S, Span](in, tx0) {
//
//      protected def map(t: TimelineView): Span = t.virtual
//    }
//
//    private final case class Impl(in: Ex[TimelineView]) extends Virtual {
//      override def productPrefix = s"TimelineView$$Virtual"  // serialization
//
//      def expand[S <: Sys[S]](implicit ctx: Ex.Context[S], tx: S#Tx): IExpr[S, Span] = {
//        import ctx.targets
//        new Expanded(in.expand[S], tx)
//      }
//    }
//  }
//  trait Virtual extends Ex[Span]

  implicit class Ops[S <: Sys[S]](private val t: Ex[TimelineView]) extends AnyVal {
    def sampleRate: Ex[Double     ] = SampleRate(t)
    def position  : Ex[Long       ] = Position  (t)
    def selection : Ex[SpanOrVoid ] = Selection (t)
    def bounds    : Ex[SpanLike   ] = Bounds    (t)
    def visible   : Ex[SpanOrVoid ] = Visible   (t)
//    def virtual   : Ex[Span       ] = Virtual   (t)
  }

  private final class Impl(
    val sampleRate: Double,
    val position  : Long,
    val selection : SpanOrVoid,
    val bounds    : SpanLike,
    val visible   : Span,
    val virtual   : Span
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
}
