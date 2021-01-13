/*
 *  TimelineToolsImpl.scala
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

import de.sciss.kollflitz.Vec
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.tool.ToolPaletteImpl
import de.sciss.mellite.{TimelineTool, TimelineTools, TimelineTrackCanvas}

import scala.swing.Component

object TimelineToolsImpl extends TimelineTools.Companion {
  def install(): Unit =
    TimelineTools.peer = this

  def apply  [T <: Txn[T]](canvas: TimelineTrackCanvas[T]): TimelineTools[T] = new ToolsImpl(canvas)
  def palette[T <: Txn[T]](control: TimelineTools[T], tools: Vec[TimelineTool[T, _]]): Component =
    new ToolPaletteImpl[T, TimelineTool[T, _]](control, tools)
}
