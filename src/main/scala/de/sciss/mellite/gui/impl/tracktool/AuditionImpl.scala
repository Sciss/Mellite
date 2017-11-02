/*
 *  AuditionImpl.scala
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
package impl
package tracktool

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{Cursor, Point, Toolkit}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.span.Span
import de.sciss.synth.proc.{AuralContext, AuralObj, TimeRef}

object AuditionImpl {
  private lazy val cursor: Cursor = {
    val tk  = Toolkit.getDefaultToolkit
    val img = ToolsImpl.getImage("cursor-audition.png")
    tk.createCustomCursor(img, new Point(4, 4), "Audition")
  }
}

/** The audition tool allows to listen to regions individually.
  * Unfortunately, this is currently quite a hackish solution:
  *
  * - We cannot determine the procs to which selected procs
  *   are linked in terms of scans
  * - We cannot issue correct transport offset play positions,
  *   this implies that the time-reference is undefined and
  *   fades won't work...
  * - We simply add all global procs to make sure that regions
  *   linked up with them play properly.
  *
  * A perhaps better solution would be to have a `AuralTimeline` somehow
  * that smartly filters the objects.
  *
  * TODO: update -- this is partly fixed now.
  */
class AuditionImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S], tlv: TimelineView[S])
  extends RegionLike[S, Unit] with RubberBand[S, Unit] {

  // import TrackTool.{Cursor => _}

  def defaultCursor: Cursor = AuditionImpl.cursor
  val name                  = "Audition"
  val icon: Icon            = GUI.iconNormal(Shapes.Audition)

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[TimelineObjView[S]]): Unit = {
    handleMouseSelection(e, regionOpt = regionOpt)

    val selMod = canvas.selectionModel
    if (selMod.isEmpty) return

    val playPos = if (!e.isAltDown) {
      // tlv.timelineModel.modifiableOption.foreach(_.position = pos) // XXX TODO -- eventually non-significant undoable edit
      pos
    } else regionOpt.fold(pos)(_.spanValue match {
      case hs: Span.HasStart => hs.start
      case _ => pos
    })

    import tlv.{cursor, workspace}
    val playOpt = cursor.step { implicit tx =>
      Mellite.auralSystem.serverOption.map { server =>
        implicit val aural: AuralContext[S] = AuralContext(server)
        val auralTimeline = AuralObj.Timeline /* .empty */(tlv.timeline)

//        (/* tlv.globalView.iterator ++ */ selMod.iterator).foreach { view =>
//          auralTimeline.addObject(view.id, view.span, view.obj)
//        }
        auralTimeline.play(TimeRef(tlv.timelineModel.bounds, offset = playPos), ())
        auralTimeline
      }
    }

    playOpt.foreach { auralTimeline =>
      val c  = e.getComponent
      val ma = new MouseAdapter {
        override def mouseReleased(e: MouseEvent): Unit = {
          cursor.step { implicit tx =>
            auralTimeline.stop()
            auralTimeline.dispose()
          }
          c.removeMouseListener(this)
        }
      }
      c.addMouseListener(ma)
    }
  }

  def commit(drag: Unit)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = None
}
