/*
 *  TimelineRenderingImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.lucre.synth.Txn
import de.sciss.mellite.TimelineTool.{Add, Cursor, Fade, Gain, Move, Mute, Patch, Resize}
import de.sciss.mellite.impl.timeline.tool.{AddImpl, AuditionImpl, CursorImpl, FadeImpl, GainImpl, MoveImpl, MuteImpl, PatchImpl, ResizeImpl}
import de.sciss.mellite.{TimelineTool, TimelineTrackCanvas, TimelineView}

object TimelineToolImpl extends TimelineTool.Companion {
  def install(): Unit =
    TimelineTool.peer = this

  def cursor  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Cursor  ] = new CursorImpl  (canvas)
  def move    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Move    ] = new MoveImpl    (canvas)
  def resize  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Resize  ] = new ResizeImpl  (canvas)
  def gain    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Gain    ] = new GainImpl    (canvas)
  def mute    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Mute    ] = new MuteImpl    (canvas)
  def fade    [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Fade    ] = new FadeImpl    (canvas)
  def function[T <: Txn[T]](canvas: TimelineTrackCanvas[T], view: TimelineView[T]): TimelineTool[T, Add] =
    new AddImpl(canvas, view)

  def patch   [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTool[T, Patch[T]] = new PatchImpl   (canvas)

  def audition[T <: Txn[T]](canvas: TimelineTrackCanvas[T], view: TimelineView[T]): TimelineTool[T, Unit] =
    new AuditionImpl(canvas, view)
}
