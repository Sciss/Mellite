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
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.tool.ToolPaletteImpl
import de.sciss.mellite.{GraphemeCanvas, GraphemeTool, GraphemeTools}
import de.sciss.model.Change
import de.sciss.model.impl.ModelImpl

import scala.swing.Component

object GraphemeToolsImpl extends GraphemeTools.Companion {
  def install(): Unit =
    GraphemeTools.peer = this

  def apply  [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTools[T] =
    new GraphemeToolsImpl(canvas)

  def palette[T <: Txn[T]](control: GraphemeTools[T], tools: Vec[GraphemeTool[T, _]]): Component =
    new ToolPaletteImpl[T, GraphemeTool[T, _]](control, tools)
}
final class GraphemeToolsImpl[T <: Txn[T]](canvas: GraphemeCanvas[T])
  extends GraphemeTools[T] with ModelImpl[GraphemeTools.Update[T]] {

  import GraphemeTools._

  private[this] var _currentTool: GraphemeTool[T, _] = GraphemeTool.cursor(canvas)
  def currentTool: GraphemeTool[T, _] = _currentTool
  def currentTool_=(value: GraphemeTool[T, _]): Unit =
    if (_currentTool != value) {
      val oldTool   = _currentTool
      _currentTool  = value
      oldTool.uninstall(canvas.canvasComponent)
      value    .install(canvas.canvasComponent)
      dispatch(ToolChanged(Change(oldTool, value)))
    }

  _currentTool.install(canvas.canvasComponent)
}