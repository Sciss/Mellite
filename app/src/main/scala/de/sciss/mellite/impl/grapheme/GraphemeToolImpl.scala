/*
 *  GraphemeToolImpl.scala
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

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.GraphemeTool.{Add, Move}
import de.sciss.mellite.impl.grapheme.tool.{AddImpl, CursorImpl, MoveImpl}
import de.sciss.mellite.{GraphemeCanvas, GraphemeTool}

object GraphemeToolImpl extends GraphemeTool.Companion {
  def install(): Unit =
    GraphemeTool.peer = this

  def cursor  [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Unit] = new CursorImpl(canvas)
  def move    [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Move] = new MoveImpl  (canvas)
  def add     [T <: Txn[T]](canvas: GraphemeCanvas[T]): GraphemeTool[T, Add ] = new AddImpl   (canvas)
}
