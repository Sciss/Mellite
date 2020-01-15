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

  def cursor  [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Unit] = new CursorImpl(canvas)
  def move    [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Move] = new MoveImpl  (canvas)
  def add     [S <: Sys[S]](canvas: GraphemeCanvas[S]): GraphemeTool[S, Add ] = new AddImpl   (canvas)
}
