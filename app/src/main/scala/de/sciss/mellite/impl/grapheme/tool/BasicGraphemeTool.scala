/*
 *  BasicGraphemeTool.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.grapheme.tool

import java.awt.event.MouseEvent

import de.sciss.lucre.synth.Txn
import de.sciss.mellite.ObjGraphemeView
import de.sciss.mellite.BasicTool.{DragAdjust, DragBegin, DragEnd}
import de.sciss.mellite.impl.tool.DraggingTool

/** Most common implementation of a grapheme tool, based on region selection and
  * mouse dragging. It implements `handleSelect` by instantiating a `Drag`
  * object. Double-clicks result in the abstract method `dialog` being called.
  * Sub-classes may choose to provide a custom dialog for double clicks by
  * and thus may return `Some` data if the dialog is positively confirmed.
  */
trait BasicGraphemeTool[T <: Txn[T], A] extends CollectionImpl[T, A] with DraggingTool[T, A, Double] {
  protected type Initial = ObjGraphemeView[T]

  final protected def handleSelect(e: MouseEvent, pos: Long, modelY: Double, region: ObjGraphemeView[T]): Unit =
    if (e.getClickCount == 2) {
      handleDoubleClick()
    } else {
      new Drag(e, modelY, pos, region)
    }

  protected def dialog(): Option[A]

  final protected def handleDoubleClick(): Unit =
    dialog().foreach { p =>
      dispatch(DragBegin)
      dispatch(DragAdjust(p))
      dispatch(DragEnd)
    }

  //  protected def showDialog(message: AnyRef): Boolean = {
  //    val op = OptionPane(message = message, messageType = OptionPane.Message.Question,
  //      optionType = OptionPane.Options.OkCancel)
  //    val result = Window.showDialog(op -> name)
  //    result == OptionPane.Result.Ok
  //  }
}
