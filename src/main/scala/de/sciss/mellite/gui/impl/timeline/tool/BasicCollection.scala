/*
 *  BasicRegion.scala
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

package de.sciss.mellite.gui.impl.timeline.tool

import java.awt.event.MouseEvent

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.BasicTool.{DragAdjust, DragBegin, DragEnd}
import de.sciss.mellite.gui.TimelineObjView
import de.sciss.mellite.gui.impl.DraggingTool

/** Most common implementation of a timeline tool, based on region selection and
  * mouse dragging. It implements `handleSelect` by instantiating a `Drag`
  * object. Double-clicks result in the abstract method `dialog` being called.
  * Sub-classes may choose to provide a custom dialog for double clicks by
  * and thus may return `Some` data if the dialog is positively confirmed.
  */
trait BasicCollection[S <: Sys[S], A] extends CollectionImpl[S, A] with DraggingTool[S, A, Int] {

  protected type Initial = TimelineObjView[S]

  final protected def handleSelect(e: MouseEvent, modelY: Int, pos: Long, child: TimelineObjView[S]): Unit =
    if (e.getClickCount == 2) {
      handleDoubleClick()
    } else {
      new Drag(e, modelY, pos, child)
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


