/*
 *  TimelineToolsImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.kollflitz.Vec
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.tool.ToolPaletteImpl
import de.sciss.mellite.{TimelineTool, TimelineTools, TimelineTrackCanvas}

import scala.swing.Component

object TimelineToolsImpl extends TimelineTools.Companion {
  def install(): Unit =
    TimelineTools.peer = this

  def apply  [S <: Sys[S]](canvas: TimelineTrackCanvas[S]): TimelineTools[S] = new ToolsImpl(canvas)
  def palette[S <: Sys[S]](control: TimelineTools[S], tools: Vec[TimelineTool[S, _]]): Component =
    new ToolPaletteImpl[S, TimelineTool[S, _]](control, tools)
}
