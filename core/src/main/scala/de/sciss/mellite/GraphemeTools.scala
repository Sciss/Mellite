/*
 *  GraphemeTools.scala
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

package de.sciss.mellite

import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Txn => LTxn}
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.model.{Change, Model}
import de.sciss.span.Span
import de.sciss.proc.FadeSpec

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component

object GraphemeTools {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply  [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTools[T]
    def palette[T <: Txn[T]](control: GraphemeTools[T], tools: Vec[GraphemeTool[T, _]]): Component
  }

  sealed trait Update[T <: LTxn[T]]
  final case class ToolChanged[T <: LTxn[T]](change: Change[GraphemeTool[T, _]]) extends Update[T]

  def apply  [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTools[T] = companion(canvas)
  def palette[T <: Txn[T]](control: GraphemeTools[T], tools: Vec[GraphemeTool[T, _]]): Component =
    companion.palette(control, tools)
}

trait GraphemeTools[T <: LTxn[T]] extends BasicTools[T, GraphemeTool[T, _], GraphemeTools.Update[T]]

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
    def cursor  [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Unit]
    def move    [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Move]
    def add     [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Add ]
  }

  def cursor  [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Unit] = companion.cursor(canvas)
  def move    [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Move] = companion.move  (canvas)
  def add     [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Add ] = companion.add   (canvas)
}

/** A tool that operates on object inside the grapheme view.
  *
  * @tparam A   the type of element that represents an ongoing
  *             edit state (typically during mouse drag).
  */
trait GraphemeTool[T <: LTxn[T], A] extends BasicTool[T, A]