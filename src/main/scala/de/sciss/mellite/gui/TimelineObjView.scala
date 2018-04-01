/*
 *  TimelineObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.IdentifierMap
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.timeline.{TimelineObjViewImpl => Impl}
import de.sciss.span.{Span, SpanLike}
import de.sciss.synth.proc.{AuxContext, FadeSpec, Timeline}

import scala.language.implicitConversions
import scala.swing.Graphics2D

object TimelineObjView {
  type SelectionModel[S <: stm.Sys[S]] = gui.SelectionModel[S, TimelineObjView[S]]

  final val Unnamed = "<unnamed>"

  /** A useful view for `RangedSeq`. It gives (start, stop) of the view's span */
  implicit def viewToPoint[S <: stm.Sys[S]](view: TimelineObjView[S]): (Long, Long) = spanToPoint(view.spanValue)

  def spanToPoint(span: SpanLike): (Long, Long) = span match {
    case Span(start, stop)  => (start, stop)
    case Span.From(start)   => (start, Long.MaxValue)
    case Span.Until(stop)   => (Long.MinValue, stop)
    case Span.All           => (Long.MinValue, Long.MaxValue)
    case Span.Void          => (Long.MinValue, Long.MinValue)
  }

  type Map[S <: stm.Sys[S]] = IdentifierMap[S#ID, S#Tx, TimelineObjView[S]]

  trait Context[S <: stm.Sys[S]] extends AuxContext[S] {
    /** A map from `TimedProc` ids to their views. This is used to establish scan links. */
    def viewMap: Map[S]
  }

  trait Factory extends ObjView.Factory {
    /** Creates a new timeline view
      *
      * @param id       the `TimedElem`'s identifier
      * @param span     the span on the timeline
      * @param obj      the object placed on the timeline
      */
    def mkTimelineView[S <: Sys[S]](id: S#ID, span: SpanLikeObj[S], obj: E[S],
                                    context: TimelineObjView.Context[S])(implicit tx: S#Tx): TimelineObjView[S]
  }

  def addFactory(f: Factory): Unit = Impl.addFactory(f)

  def factories: Iterable[Factory] = Impl.factories

  def apply[S <: Sys[S]](timed: Timeline.Timed[S], context: Context[S])(implicit tx: S#Tx): TimelineObjView[S] =
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
trait TimelineObjView[S <: stm.Sys[S]] extends ObjView[S] {

  def spanH: stm.Source[S#Tx, SpanLikeObj[S]]

  def span(implicit tx: S#Tx): SpanLikeObj[S]

  def id(implicit tx: S#Tx): S#ID // Timeline.Timed[S]

  var spanValue: SpanLike

  var trackIndex : Int
  var trackHeight: Int

  def paintBack (g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit
  def paintFront(g: Graphics2D, tlv: TimelineView[S], r: TimelineRendering): Unit
}