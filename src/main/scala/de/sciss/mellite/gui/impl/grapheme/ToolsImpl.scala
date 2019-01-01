/*
 *  ToolsImpl.scala
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

package de.sciss.mellite.gui.impl.grapheme

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{GraphemeCanvas, GraphemeTool, GraphemeTools}
import de.sciss.model.Change
import de.sciss.model.impl.ModelImpl

final class ToolsImpl[S <: Sys[S]](canvas: GraphemeCanvas[S])
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