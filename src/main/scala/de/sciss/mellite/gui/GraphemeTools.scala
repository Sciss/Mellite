/*
 *  GraphemeTools.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.BasicTool.DragRubber
import de.sciss.mellite.gui.impl.grapheme.ToolsImpl
import de.sciss.mellite.gui.impl.grapheme.tool.{CursorImpl, MoveImpl}
import de.sciss.mellite.gui.impl.tool.ToolPaletteImpl
import de.sciss.model.{Change, Model}
import de.sciss.span.Span
import de.sciss.synth.proc.FadeSpec

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component

object GraphemeTools {
  sealed trait Update[S <: stm.Sys[S]]
  final case class ToolChanged[S <: stm.Sys[S]](change: Change[GraphemeTool[S, _]]) extends Update[S]

  def apply  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTools[S] = new ToolsImpl(canvas)
  def palette[S <: Sys[S]](control: GraphemeTools[S], tools: Vec[GraphemeTool[S, _]]): Component =
    new ToolPaletteImpl[S, GraphemeTool[S, _]](control, tools)
}

trait GraphemeTools[S <: stm.Sys[S]] extends BasicTools[S, GraphemeTool[S, _], GraphemeTools.Update[S]]

object GraphemeTool {
  trait Rectangular {
    def modelYOffset: Double
    def modelYExtent: Double
    def span: Span

//    def isValid: Boolean = modelYOffset >= 0
  }

  type Update[+A] = BasicTool.Update[A]

  val EmptyRubber: DragRubber[Double] = DragRubber(-1d, -1d, Span(0L, 0L), isValid = false)

  // ----

  final case class Move(deltaTime: Long, deltaModelY: Double, copy: Boolean) {
    override def toString = s"$productPrefix(deltaTime = $deltaTime, deltaModelY = $deltaModelY, copy = $copy)"
  }

  final val NoMove = Move(deltaTime = 0L, deltaModelY = 0.0, copy = false)

//  final case class Cursor(name: Option[String])

  final val EmptyFade = FadeSpec(numFrames = 0L)

  type Listener = Model.Listener[Update[Any]]

  def cursor  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Unit] = new CursorImpl(canvas)
  def move    [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Move] = new MoveImpl  (canvas)
}

/** A tool that operates on object inside the grapheme view.
  *
  * @tparam A   the type of element that represents an ongoing
  *             edit state (typically during mouse drag).
  */
trait GraphemeTool[S <: stm.Sys[S], A] extends BasicTool[S, A]