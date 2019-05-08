/*
 *  Timed.scala
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

package de.sciss.lucre.expr.graph

import de.sciss.lucre.event.ITargets
import de.sciss.lucre.expr.graph.impl.MappedIExpr
import de.sciss.lucre.expr.{Context, IExpr}
import de.sciss.lucre.stm.Sys
import de.sciss.span.SpanLike

// XXX TODO --- this should go into SP
object Timed {
  private final class SpanExpanded[S <: Sys[S], A](in: IExpr[S, Timed[A]], tx0: S#Tx)
                                                  (implicit targets: ITargets[S])
    extends MappedIExpr[S, Timed[A], SpanLike](in, tx0) {
    
    protected def mapValue(inValue: Timed[A]): SpanLike = inValue.span
  }

  final case class Span[A](in: Ex[Timed[A]]) extends Ex[SpanLike] {
    type Repr[S <: Sys[S]] = IExpr[S, SpanLike]

    override def productPrefix = s"Timed$$Span"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new SpanExpanded(in.expand[S], tx)
    }
  }

  private final class ValueExpanded[S <: Sys[S], A](in: IExpr[S, Timed[A]], tx0: S#Tx)
                                                   (implicit targets: ITargets[S])
    extends MappedIExpr[S, Timed[A], A](in, tx0) {

    protected def mapValue(inValue: Timed[A]): A = inValue.value
  }

  final case class Value[A](in: Ex[Timed[A]]) extends Ex[A] {
    type Repr[S <: Sys[S]] = IExpr[S, A]

    override def productPrefix = s"Timed$$Value"  // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new ValueExpanded(in.expand[S], tx)
    }
  }

  implicit class ExOps[A](private val t: Ex[Timed[A]]) extends AnyVal {
    // def collect[B: Obj.Selector]: Ex[Timed[B]] = ...

    def span  : Ex[SpanLike]  = Span  (t)
    def value : Ex[A]         = Value (t)
  }
}
final case class Timed[+A] private[graph] (span: SpanLike, value: A)
