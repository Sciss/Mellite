/*
 *  AuditionImpl.scala
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

package de.sciss.mellite.impl.timeline.tool

import java.awt
import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{Point, Toolkit}

import de.sciss.lucre.Cursor
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.tool.{CollectionToolLike, RubberBandTool}
import de.sciss.mellite.{GUI, ObjTimelineView, Shapes, TimelineTool, TimelineTrackCanvas, TimelineView}
import de.sciss.span.Span
import de.sciss.proc.{AuralContext, AuralObj, TimeRef}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object AuditionImpl {
  private lazy val cursor: awt.Cursor = {
    val tk  = Toolkit.getDefaultToolkit
    val img = GUI.getImage("cursor-audition.png")
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
class AuditionImpl[T <: Txn[T]](protected val canvas: TimelineTrackCanvas[T], tlv: TimelineView[T])
  extends CollectionToolLike[T, Unit, Int, ObjTimelineView[T]]
    with RubberBandTool[T, Unit, Int, ObjTimelineView[T]]
    with TimelineTool[T, Unit] {

  // import TrackTool.{Cursor => _}

  def defaultCursor: awt.Cursor = AuditionImpl.cursor
  val name                  = "Audition"
  val icon: Icon            = GUI.iconNormal(Shapes.Audition)

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[ObjTimelineView[T]]): Unit = {
    handleMouseSelection(e, childOpt = regionOpt)

    val selMod = canvas.selectionModel
    if (selMod.isEmpty) return

    val playPos = if (!e.isAltDown) {
      // tlv.timelineModel.modifiableOption.foreach(_.position = pos) // XXX TODO -- eventually non-significant undoable edit
      pos
    } else regionOpt.fold(pos)(_.spanValue match {
      case hs: Span.HasStart => hs.start
      case _ => pos
    })

    import tlv.{cursor, universe}
    val playOpt = cursor.step { implicit tx =>
      universe.auralSystem.serverOption.map { server =>
        implicit val aural: AuralContext[T] = AuralContext(server)
        val auralTimeline = AuralObj.Timeline /* .empty */(tlv.obj)

//        (/* tlv.globalView.iterator ++ */ selMod.iterator).foreach { view =>
//          auralTimeline.addObject(view.id, view.span, view.obj)
//        }
        val span = tlv.timelineModel.bounds match {
          case hs: Span.HasStart => hs
          case _ => Span.from(0L)
        }
        auralTimeline.run(TimeRef(span, offset = playPos), ())
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

  def commit(drag: Unit)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = None
}
