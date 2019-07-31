package de.sciss.mellite.gui.impl.timeline

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{TimelineTool, TimelineTrackCanvas, TimelineView}
import de.sciss.mellite.TimelineTool.{Add, Cursor, Fade, Gain, Move, Mute, Patch, Resize}
import de.sciss.mellite.gui.impl.timeline.tool.{AddImpl, AuditionImpl, CursorImpl, FadeImpl, GainImpl, MoveImpl, MuteImpl, PatchImpl, ResizeImpl}

object TimelineToolImpl extends TimelineTool.Companion {
  def install(): Unit =
    TimelineTool.peer = this

  def cursor  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Cursor  ] = new CursorImpl  (canvas)
  def move    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Move    ] = new MoveImpl    (canvas)
  def resize  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Resize  ] = new ResizeImpl  (canvas)
  def gain    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Gain    ] = new GainImpl    (canvas)
  def mute    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Mute    ] = new MuteImpl    (canvas)
  def fade    [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Fade    ] = new FadeImpl    (canvas)
  def function[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TimelineTool[S, Add] =
    new AddImpl(canvas, view)

  def patch   [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTool[S, Patch[S]] = new PatchImpl   (canvas)

  def audition[S <: Sys[S]](canvas: TimelineTrackCanvas[S], view: TimelineView[S]): TimelineTool[S, Unit] =
    new AuditionImpl(canvas, view)
}
