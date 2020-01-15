/*
 *  EditStreamPeer.scala
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

package de.sciss.mellite.edit

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.patterns.lucre.{Context => LContext, Stream => LStream}
import de.sciss.patterns.{Context => PContext, Stream => PStream}
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

object EditStreamPeer {
  @deprecated("Try to transition to stm.UndoManager", since = "1.17.0")
  def apply[S <: Sys[S]](name: String, stream: LStream[S], value: PStream[S, Any])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val streamH = tx.newHandle(stream)
    implicit val ctx: PContext[S] = LContext[S](tx.system, tx)
//    implicitly[Serializer[S#Tx, S#Acc, PStream[S, Any]]]
    val beforeH = tx.newHandle(stream.peer())
    val nowH    = tx.newHandle(value)
    val res     = new Impl(name, streamH, beforeH, nowH)
    res.perform()
    res
  }

  private final class Impl[S <: Sys[S]](name: String,
                                        streamH: stm.Source[S#Tx, LStream[S]],
                                        beforeH: stm.Source[S#Tx, PStream[S, Any]],
                                        nowH   : stm.Source[S#Tx, PStream[S, Any]])(implicit cursor: stm.Cursor[S])
    extends AbstractUndoableEdit {

    override def undo(): Unit = {
      super.undo()
      cursor.step { implicit tx =>
        val stream    = streamH()
        stream.peer() = beforeH()
      }
    }

    override def redo(): Unit = {
      super.redo()
      cursor.step { implicit tx => perform() }
    }

    def perform()(implicit tx: S#Tx): Unit = {
      val stream    = streamH()
      stream.peer() = nowH()
    }

    override def getPresentationName: String = name
  }
}