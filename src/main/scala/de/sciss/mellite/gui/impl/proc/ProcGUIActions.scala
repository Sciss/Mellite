/*
 *  ProcGUIActions.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl.proc

import javax.swing.undo.UndoableEdit

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.stm
import de.sciss.lucre.swing.requireEDT
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditTimelineRemoveObj
import de.sciss.synth.proc.Timeline

import scala.collection.breakOut
import scala.collection.immutable.{IndexedSeq => Vec}

/** These actions require being executed on the EDT. */
object ProcGUIActions {
  // scalac still has bug finding Timeline.Modifiable
  private type TimelineMod[S <: Sys[S]] = Timeline.Modifiable[S] // , Proc[S], Obj.UpdateT[S, Proc.Elem[S]]]

  def removeProcs[S <: Sys[S]](group: TimelineMod[S], views: TraversableOnce[TimelineObjView[S]])
                              (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    requireEDT()
    val name = "Remove Object"
    val edits = views.flatMap { pv0 =>
      val span  = pv0.span
      val obj   = pv0.obj

      val editsUnlink: Vec[UndoableEdit] = pv0 match {
        case pv: ProcObjView.Timeline[S] =>
          val edits     = pv.targets.flatMap(_.remove())(breakOut): Vec[UndoableEdit]
          edits

        case _ => Vector.empty
      }

      // group.remove(span, obj)
      editsUnlink :+ EditTimelineRemoveObj(name, group, span, obj)
    } .toList

    CompoundEdit(edits, name)
  }
}
