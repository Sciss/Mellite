/*
 *  TimelineTools.scala
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

import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.model.{Change, Model}
import de.sciss.span.Span
import de.sciss.proc.FadeSpec

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
    def apply  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTools[T]
    def palette[T <: Txn[T]](control: TimelineTools[T], tools: Vec[TimelineTool[T, _]]): Component
  }

  sealed trait Update[T <: LTxn[T]]
  final case class ToolChanged          [T <: LTxn[T]](change: Change[TimelineTool[T, _]]) extends Update[T]
  final case class VisualBoostChanged   [T <: LTxn[T]](change: Change[Float             ]) extends Update[T]
  final case class FadeViewModeChanged  [T <: LTxn[T]](change: Change[FadeViewMode      ]) extends Update[T]
  final case class RegionViewModeChanged[T <: LTxn[T]](change: Change[RegionViewMode    ]) extends Update[T]

  def apply  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTools[T] = companion(canvas)
  def palette[T <: Txn[T]](control: TimelineTools[T], tools: Vec[TimelineTool[T, _]]): Component =
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

trait TimelineTools[T <: LTxn[T]] extends BasicTools[T, TimelineTool[T, _], TimelineTools.Update[T]] {
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
    def cursor  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Cursor  ]
    def move    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Move    ]
    def resize  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Resize  ]
    def gain    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Gain    ]
    def mute    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Mute    ]
    def fade    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Fade    ]
    def function[T <: Txn[T]](canvas: TimelineTrackCanvas[T], view: TimelineView[T]): TimelineTool[T, Add]
    def patch   [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Patch[T]]
    def audition[T <: Txn[T]](canvas: TimelineTrackCanvas[T], view: TimelineView[T]): TimelineTool[T, Unit]
  }

  trait Rectangular extends BasicTool.Rectangular[Int] {
    final def isValid: Boolean = modelYOffset >= 0
  }

  type Update[+A] = BasicTool.Update[A]

  val EmptyRubber: DragRubber[Int] = DragRubber(0, 0, Span(0L, 0L), isValid = false)

  // ----

  type Move                              = ProcActions.Move
  val  Move   : ProcActions.Move   .type = ProcActions.Move
  type Resize                            = ProcActions.Resize
  val  Resize : ProcActions.Resize .type = ProcActions.Resize

  final case class Gain    (factor: Float)
  final case class Mute    (engaged: Boolean)
  final case class Fade    (deltaFadeIn: Long, deltaFadeOut: Long, deltaFadeInCurve: Float, deltaFadeOutCurve: Float)

  final val NoMove      = Move(deltaTime = 0L, deltaTrack = 0, copy = false)
  final val NoResize    = Resize(deltaStart = 0L, deltaStop = 0L, deltaTrackStart = 0, deltaTrackStop = 0)
  final val NoGain      = Gain(1f)
  final val NoFade      = Fade(0L, 0L, 0f, 0f)
  final val NoFunction  = Add(-1, -1, Span(0L, 0L))

  final case class Add(modelYOffset: Int, modelYExtent: Int, span: Span)
    extends Update[Nothing] with Rectangular

  final case class Cursor  (name: Option[String])

  object Patch {
    sealed trait Sink[+S]
    case class Linked[T <: Txn[T]](proc: ObjTimelineView[T] /*ProcObjView.Timeline[T]*/) extends Sink[T]
    case class Unlinked(frame: Long, y: Int) extends Sink[Nothing]
  }
  final case class Patch[T <: Txn[T]](source: ObjTimelineView[T] /*ProcObjView.Timeline[T]*/, sink: Patch.Sink[T])

  final val EmptyFade = FadeSpec(numFrames = 0L)

  type Listener = Model.Listener[Update[Any]]

  def cursor  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Cursor  ] = companion.cursor  (canvas)
  def move    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Move    ] = companion.move    (canvas)
  def resize  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Resize  ] = companion.resize  (canvas)
  def gain    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Gain    ] = companion.gain    (canvas)
  def mute    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Mute    ] = companion.mute    (canvas)
  def fade    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Fade    ] = companion.fade    (canvas)
  def patch   [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Patch[T]] = companion.patch(   canvas)

  def function[T <: Txn[T]](canvas: TimelineTrackCanvas[T], view: TimelineView[T]): TimelineTool[T, Add] =
    companion.function(canvas, view)

  def audition[T <: Txn[T]](canvas: TimelineTrackCanvas[T], view: TimelineView[T]): TimelineTool[T, Unit] =
    companion.audition(canvas, view)
}

/** A tool that operates on object inside the timeline view.
  *
  * @tparam A   the type of element that represents an ongoing
  *             edit state (typically during mouse drag).
  */
trait TimelineTool[T <: LTxn[T], A] extends BasicTool[T, A]
