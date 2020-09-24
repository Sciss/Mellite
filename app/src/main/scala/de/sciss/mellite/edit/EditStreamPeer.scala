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
  def apply[T <: Txn[T]](name: String, stream: LStream[T], value: PStream[T, Any])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val streamH = tx.newHandle(stream)
    implicit val ctx: PContext[T] = LContext[T](tx.system, tx)
//    implicitly[Serializer[T, S#Acc, PStream[T, Any]]]
    val beforeH = tx.newHandle(stream.peer())
    val nowH    = tx.newHandle(value)
    val res     = new Impl(name, streamH, beforeH, nowH)
    res.perform()
    res
  }

  private final class Impl[T <: Txn[T]](name: String,
                                        streamH: Source[T, LStream[T]],
                                        beforeH: Source[T, PStream[T, Any]],
                                        nowH   : Source[T, PStream[T, Any]])(implicit cursor: Cursor[T])
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

    def perform()(implicit tx: T): Unit = {
      val stream    = streamH()
      stream.peer() = nowH()
    }

    override def getPresentationName: String = name
  }
}