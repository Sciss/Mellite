/*
 *  TrackTools.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import java.awt.Cursor
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.mellite.gui.impl.tracktool.{AuditionImpl, CursorImpl, FadeImpl, FunctionImpl, GainImpl, MoveImpl, MuteImpl, PaletteImpl, PatchImpl, ResizeImpl, ToolsImpl}
import de.sciss.model.{Change, Model}
import de.sciss.span.Span
import de.sciss.synth.proc.FadeSpec

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component

object TrackTools {
  sealed trait Update[S <: stm.Sys[S]]
  final case class ToolChanged          [S <: stm.Sys[S]](change: Change[TrackTool[S, _]]) extends Update[S]
  final case class VisualBoostChanged   [S <: stm.Sys[S]](change: Change[Float          ]) extends Update[S]
  final case class FadeViewModeChanged  [S <: stm.Sys[S]](change: Change[FadeViewMode   ]) extends Update[S]
  final case class RegionViewModeChanged[S <: stm.Sys[S]](change: Change[RegionViewMode ]) extends Update[S]

  def apply  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTools[S] = new ToolsImpl(canvas)
  def palette[S <: Sys[S]](control: TrackTools[S], tools: Vec[TrackTool[S, _]]): Component =
    new PaletteImpl[S](control, tools)
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

trait TrackTools[S <: stm.Sys[S]] extends Model[TrackTools.Update[S]] {
  var currentTool   : TrackTool[S, _]
  var visualBoost   : Float
  var fadeViewMode  : FadeViewMode
  var regionViewMode: RegionViewMode
}

object TrackTool {
  trait Rectangular {
    def trackIndex: Int
    def trackHeight: Int
    def span: Span

    def isValid: Boolean = trackIndex >= 0
  }

  sealed trait Update[+A]
  case object DragBegin extends Update[Nothing]
  final case class DragAdjust[A](value: A) extends Update[A]

  final case class DragRubber(trackIndex: Int, trackHeight: Int, span: Span)
    extends Update[Nothing] with Rectangular

  case object DragEnd    extends Update[Nothing] // (commit: AbstractCompoundEdit)
  case object DragCancel extends Update[Nothing]

  /** Direct adjustment without drag period. */
  case class Adjust[A](value: A) extends Update[A]

  val EmptyRubber = DragRubber(-1, -1, Span(0L, 0L))

  // ----

  type Move   = ProcActions.Move
  val  Move   = ProcActions.Move
  type Resize = ProcActions.Resize
  val  Resize = ProcActions.Resize
  final case class Gain    (factor: Float)
  final case class Mute    (engaged: Boolean)
  final case class Fade    (deltaFadeIn: Long, deltaFadeOut: Long, deltaFadeInCurve: Float, deltaFadeOutCurve: Float)

  final val NoMove      = Move(deltaTime = 0L, deltaTrack = 0, copy = false)
  final val NoResize    = Resize(deltaStart = 0L, deltaStop = 0L)
  final val NoGain      = Gain(1f)
  final val NoFade      = Fade(0L, 0L, 0f, 0f)
  final val NoFunction  = Function(-1, -1, Span(0L, 0L))

  final case class Function(trackIndex: Int, trackHeight: Int, span: Span)
    extends Update[Nothing] with Rectangular

  final case class Cursor  (name: Option[String])

  object Patch {
    sealed trait Sink[+S]
    case class Linked[S <: Sys[S]](proc: ProcObjView.Timeline[S]) extends Sink[S]
    case class Unlinked(frame: Long, y: Int) extends Sink[Nothing]
  }
  final case class Patch[S <: Sys[S]](source: ProcObjView.Timeline[S], sink: Patch.Sink[S])

  final val EmptyFade = FadeSpec(numFrames = 0L)

  type Listener = Model.Listener[Update[Any]]

  def cursor  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Cursor  ] = new CursorImpl  (canvas)
  def move    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Move    ] = new MoveImpl    (canvas)
  def resize  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Resize  ] = new ResizeImpl  (canvas)
  def gain    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Gain    ] = new GainImpl    (canvas)
  def mute    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Mute    ] = new MuteImpl    (canvas)
  def fade    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Fade    ] = new FadeImpl    (canvas)
  def function[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TrackTool[S, Function] =
    new FunctionImpl(canvas, view)

  def patch   [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TrackTool[S, Patch[S]] = new PatchImpl   (canvas)

  def audition[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TrackTool[S, Unit] =
    new AuditionImpl(canvas, view)
}

/** A tool that operates on object inside the timeline view.
  *
  * @tparam A   the type of element that represents an ongoing
  *             edit state (typically during mouse drag).
  */
trait TrackTool[S <: stm.Sys[S], A] extends Model[TrackTool.Update[A]] {
  /** The mouse cursor used when the tool is active. */
  def defaultCursor: Cursor
  /** The icon to use in a tool bar. */
  def icon: Icon
  /** The human readable name of the tool. */
  def name: String

  /** Called to activate the tool to operate on the given component. */
  def install  (component: Component): Unit
  /** Called to deactivate the tool before switching to a different tool. */
  def uninstall(component: Component): Unit

  // def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[TimelineProcView[S]]): Unit

  /** Called after the end of a mouse drag gesture. If this constitutes a
    * valid edit, the method should return the resulting undoable edit.
    *
    * @param drag   the last editing state
    * @param cursor the cursor that might be needed to construct the undoable edit
    * @return either `Some` edit or `None` if the action does not constitute an
    *         edit or the edit parameters are invalid.
    */
  def commit(drag: A)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]
}

//object TrackSlideTool {
//  case class Slide(deltaOuter: Long, deltaInner: Long)
//}
//
//class TrackSlideTool(trackList: TrackList, timelineModel: TimelineView)
//  extends BasicTrackRegionTool[TrackSlideTool.Slide](trackList, timelineModel) {
//
//  import TrackSlideTool._
//
//  def defaultCursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
//
//  val name = "Slide"
//
//  protected def dialog: Option[Slide] = None // not yet supported
//
//  protected def dragToParam(d: Drag): Slide = {
//    val amt = d.currentPos - d.firstPos
//    if (d.firstEvent.isAltDown)
//      Slide(0L, -amt)
//    else
//      Slide(amt, 0L)
//  }
//}
