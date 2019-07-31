/*
 *  TimelineTools.scala
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

package de.sciss.mellite

import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.model.{Change, Model}
import de.sciss.span.Span
import de.sciss.synth.proc.FadeSpec

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component

object TimelineTools {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTools[S]
    def palette[S <: Sys[S]](control: TimelineTools[S], tools: Vec[TimelineTool[S, _]]): Component
  }

  sealed trait Update[S <: stm.Sys[S]]
  final case class ToolChanged          [S <: stm.Sys[S]](change: Change[TimelineTool[S, _]]) extends Update[S]
  final case class VisualBoostChanged   [S <: stm.Sys[S]](change: Change[Float             ]) extends Update[S]
  final case class FadeViewModeChanged  [S <: stm.Sys[S]](change: Change[FadeViewMode      ]) extends Update[S]
  final case class RegionViewModeChanged[S <: stm.Sys[S]](change: Change[RegionViewMode    ]) extends Update[S]

  def apply  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTools[S] = companion(canvas)
  def palette[S <: Sys[S]](control: TimelineTools[S], tools: Vec[TimelineTool[S, _]]): Component =
    companion.palette(control, tools)
}

object RegionViewMode {
  /** No visual indicator for region borders */
  case object None      extends RegionViewMode { final val id = 0 }
  /** Rounded box for region borders */
  case object Box       extends RegionViewMode { final val id = 1 }
  /** Rounded box with region name for region borders */
  case object TitledBox extends RegionViewMode { final val id = 2 }

  def apply(id: Int): RegionViewMode = (id: @switch) match {
    case None     .id => None
    case Box      .id => Box
    case TitledBox.id => TitledBox
  }
}
sealed trait RegionViewMode { def id: Int }

object FadeViewMode {
  /** No visual indicator for fades */
  case object None     extends FadeViewMode { final val id = 0 }
  /** Curve overlays to indicate fades */
  case object Curve    extends FadeViewMode { final val id = 1 }
  /** Gain adjustments to sonogram to indicate fades */
  case object Sonogram extends FadeViewMode { final val id = 2 }

  def apply(id: Int): FadeViewMode = (id: @switch) match {
    case None    .id  => None
    case Curve   .id  => Curve
    case Sonogram.id  => Sonogram
  }
}
sealed trait FadeViewMode {
  def id: Int
}

trait TimelineTools[S <: stm.Sys[S]] extends BasicTools[S, TimelineTool[S, _], TimelineTools.Update[S]] {
  var visualBoost   : Float
  var fadeViewMode  : FadeViewMode
  var regionViewMode: RegionViewMode
}

object TimelineTool {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def cursor  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Cursor  ]
    def move    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Move    ]
    def resize  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Resize  ]
    def gain    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Gain    ]
    def mute    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Mute    ]
    def fade    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Fade    ]
    def function[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TimelineTool[S, Add]
    def patch   [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Patch[S]]
    def audition[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TimelineTool[S, Unit]
  }

  trait Rectangular extends BasicTool.Rectangular[Int] {
    final def isValid: Boolean = modelYOffset >= 0
  }

  type Update[+A] = BasicTool.Update[A]

  val EmptyRubber: DragRubber[Int] = DragRubber(0, 0, Span(0L, 0L), isValid = false)

  // ----

  type Move                             = ProcActions.Move
  val  Move   : ProcActions.Move  .type = ProcActions.Move
  type Resize                           = ProcActions.Resize
  val  Resize : ProcActions.Resize.type = ProcActions.Resize

  final case class Gain    (factor: Float)
  final case class Mute    (engaged: Boolean)
  final case class Fade    (deltaFadeIn: Long, deltaFadeOut: Long, deltaFadeInCurve: Float, deltaFadeOutCurve: Float)

  final val NoMove      = Move(deltaTime = 0L, deltaTrack = 0, copy = false)
  final val NoResize    = Resize(deltaStart = 0L, deltaStop = 0L)
  final val NoGain      = Gain(1f)
  final val NoFade      = Fade(0L, 0L, 0f, 0f)
  final val NoFunction  = Add(-1, -1, Span(0L, 0L))

  final case class Add(modelYOffset: Int, modelYExtent: Int, span: Span)
    extends Update[Nothing] with Rectangular

  final case class Cursor  (name: Option[String])

  object Patch {
    sealed trait Sink[+S]
    case class Linked[S <: Sys[S]](proc: ObjTimelineView[S] /*ProcObjView.Timeline[S]*/) extends Sink[S]
    case class Unlinked(frame: Long, y: Int) extends Sink[Nothing]
  }
  final case class Patch[S <: Sys[S]](source: ObjTimelineView[S] /*ProcObjView.Timeline[S]*/, sink: Patch.Sink[S])

  final val EmptyFade = FadeSpec(numFrames = 0L)

  type Listener = Model.Listener[Update[Any]]

  def cursor  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Cursor  ] = companion.cursor  (canvas)
  def move    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Move    ] = companion.move    (canvas)
  def resize  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Resize  ] = companion.resize  (canvas)
  def gain    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Gain    ] = companion.gain    (canvas)
  def mute    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Mute    ] = companion.mute    (canvas)
  def fade    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Fade    ] = companion.fade    (canvas)
  def patch   [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Patch[S]] = companion.patch(   canvas)

  def function[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TimelineTool[S, Add] =
    companion.function(canvas, view)

  def audition[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TimelineTool[S, Unit] =
    companion.audition(canvas, view)
}

/** A tool that operates on object inside the timeline view.
  *
  * @tparam A   the type of element that represents an ongoing
  *             edit state (typically during mouse drag).
  */
trait TimelineTool[S <: stm.Sys[S], A] extends BasicTool[S, A]
