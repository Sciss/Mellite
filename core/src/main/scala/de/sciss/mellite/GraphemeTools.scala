/*
 *  GraphemeTools.scala
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

package de.sciss.mellite

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.model.{Change, Model}
import de.sciss.span.Span
import de.sciss.synth.proc.FadeSpec

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component

object GraphemeTools {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTools[S]
    def palette[S <: Sys[S]](control: GraphemeTools[S], tools: Vec[GraphemeTool[S, _]]): Component
  }

  sealed trait Update[S <: stm.Sys[S]]
  final case class ToolChanged[S <: stm.Sys[S]](change: Change[GraphemeTool[S, _]]) extends Update[S]

  def apply  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTools[S] = companion(canvas)
  def palette[S <: Sys[S]](control: GraphemeTools[S], tools: Vec[GraphemeTool[S, _]]): Component =
    companion.palette(control, tools)
}

trait GraphemeTools[S <: stm.Sys[S]] extends BasicTools[S, GraphemeTool[S, _], GraphemeTools.Update[S]]

object GraphemeTool {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  type Update[+A] = BasicTool.Update[A]

  val EmptyRubber: DragRubber[Double] = DragRubber(0d, 0d, Span(0L, 0L), isValid = false)

  // ----

  final case class Move(deltaTime: Long, deltaModelY: Double, copy: Boolean) {
    override def toString = s"$productPrefix(deltaTime = $deltaTime, deltaModelY = $deltaModelY, copy = $copy)"
  }

  final case class Add(time: Long, modelY: Double, tpe: Obj.Type)

  final val NoMove = Move(deltaTime = 0L, deltaModelY = 0.0, copy = false)

//  final case class Cursor(name: Option[String])

  final val EmptyFade = FadeSpec(numFrames = 0L)

  type Listener = Model.Listener[Update[Any]]

  private[mellite] trait Companion {
    def cursor  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Unit]
    def move    [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Move]
    def add     [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Add ]
  }

  def cursor  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Unit] = companion.cursor(canvas)
  def move    [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Move] = companion.move  (canvas)
  def add     [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Add ] = companion.add   (canvas)
}

/** A tool that operates on object inside the grapheme view.
  *
  * @tparam A   the type of element that represents an ongoing
  *             edit state (typically during mouse drag).
  */
trait GraphemeTool[S <: stm.Sys[S], A] extends BasicTool[S, A]