/*
 *  ObjTimelineView.scala
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

package de.sciss.mellite

import de.sciss.lucre.{Ident, IdentMap, Source, SpanLikeObj, Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite
import de.sciss.mellite.impl.{ObjTimelineViewImpl => Impl}
import de.sciss.span.{Span, SpanLike}
import de.sciss.proc.{AuxContext, FadeSpec, Timeline}

import scala.language.implicitConversions
import scala.swing.Graphics2D

object ObjTimelineView {
  type SelectionModel[T <: LTxn[T]] = mellite.SelectionModel[T, ObjTimelineView[T]]

  /** A useful view for `RangedSeq`. It gives (start, stop) of the view's span */
  implicit def viewToPoint[T <: LTxn[T]](view: ObjTimelineView[T]): (Long, Long) = spanToPoint(view.spanValue)

  def spanToPoint(span: SpanLike): (Long, Long) = span match {
    case Span(start, stop)  => (start, stop)
    case Span.From(start)   => (start, Long.MaxValue)
    case Span.Until(stop)   => (Long.MinValue, stop)
    case Span.All           => (Long.MinValue, Long.MaxValue)
    case Span.Void          => (Long.MinValue, Long.MinValue)
  }

  type Map[T <: LTxn[T]] = IdentMap[T, ObjTimelineView[T]]

  trait Context[T <: LTxn[T]] extends AuxContext[T] {
    /** A map from `TimedProc` ids to their views. This is used to establish scan links. */
    def viewMap: Map[T]
  }

  trait Factory extends ObjView.Factory {
    /** Creates a new timeline view
      *
      * @param id       the `TimedElem`'s identifier
      * @param span     the span on the timeline
      * @param obj      the object placed on the timeline
      */
    def mkTimelineView[T <: Txn[T]](id: Ident[T], span: SpanLikeObj[T], obj: E[T],
                                    context: ObjTimelineView.Context[T])(implicit tx: T): ObjTimelineView[T]
  }

  def addFactory(f: Factory): Unit = Impl.addFactory(f)

  def factories: Iterable[Factory] = Impl.factories

  def apply[T <: Txn[T]](timed: Timeline.Timed[T], context: Context[T])(implicit tx: T): ObjTimelineView[T] =
    Impl(timed, context)

  // ---- specialization ----

  final val attrTrackIndex  = "track-index"
  final val attrTrackHeight = "track-height"

  trait HasMute {
    var muted: Boolean
  }

  trait HasGain {
    var gain: Double
  }

  trait HasFade {
    var fadeIn : FadeSpec
    var fadeOut: FadeSpec
  }
}
trait ObjTimelineView[T <: LTxn[T]] extends ObjView[T] {

  def spanH: Source[T, SpanLikeObj[T]]

  def span(implicit tx: T): SpanLikeObj[T]

  def id(implicit tx: T): Ident[T] // Timeline.Timed[T]

  var spanValue: SpanLike

  var trackIndex : Int
  var trackHeight: Int

  def paintBack (g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering): Unit
  def paintFront(g: Graphics2D, tlv: TimelineView[T], r: TimelineRendering): Unit
}