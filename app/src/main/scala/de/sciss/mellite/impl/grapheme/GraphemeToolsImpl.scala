/*
 *  GraphemeToolsImpl.scala
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

package de.sciss.mellite.impl.grapheme

import de.sciss.kollflitz.Vec
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.tool.ToolPaletteImpl
import de.sciss.mellite.{GraphemeCanvas, GraphemeTool, GraphemeTools}
import de.sciss.model.Change
import de.sciss.model.impl.ModelImpl

import scala.swing.Component

object GraphemeToolsImpl extends GraphemeTools.Companion {
  def install(): Unit =
    GraphemeTools.peer = this

  def apply  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTools[S] =
    new GraphemeToolsImpl(canvas)

  def palette[S <: Sys[S]](control: GraphemeTools[S], tools: Vec[GraphemeTool[S, _]]): Component =
    new ToolPaletteImpl[S, GraphemeTool[S, _]](control, tools)
}
final class GraphemeToolsImpl[S <: Sys[S]](canvas: GraphemeCanvas[S])
  extends GraphemeTools[S] with ModelImpl[GraphemeTools.Update[S]] {

  import GraphemeTools._

  private[this] var _currentTool: GraphemeTool[S, _] = GraphemeTool.cursor(canvas)
  def currentTool: GraphemeTool[S, _] = _currentTool
  def currentTool_=(value: GraphemeTool[S, _]): Unit =
    if (_currentTool != value) {
      val oldTool   = _currentTool
      _currentTool  = value
      oldTool.uninstall(canvas.canvasComponent)
      value    .install(canvas.canvasComponent)
      dispatch(ToolChanged(Change(oldTool, value)))
    }

  _currentTool.install(canvas.canvasComponent)
}