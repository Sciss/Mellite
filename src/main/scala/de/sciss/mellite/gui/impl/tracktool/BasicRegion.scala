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

package de.sciss.mellite
package gui
package impl
package tracktool

import java.awt.event.MouseEvent

import de.sciss.lucre.synth.Sys

object BasicRegion {
  final val MinDur  = 32
}

/** Most common implementation of a track tool, based on region selection and
  * mouse dragging. It implements `handleSelect` by instantiating a `Drag`
  * object. Double-clicks result in the abstract method `dialog` being called.
  * Sub-classes may choose to provide a custom dialog for double clicks by
  * and thus may return `Some` data if the dialog is positively confirmed.
  */
trait BasicRegion[S <: Sys[S], A] extends RegionImpl[S, A] with Dragging[S, A] {
  import TrackTool._

  protected type Initial = TimelineObjView[S]

  final protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: TimelineObjView[S]): Unit =
    if (e.getClickCount == 2) {
      handleDoubleClick()
    } else {
      new Drag(e, hitTrack, pos, region)
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


