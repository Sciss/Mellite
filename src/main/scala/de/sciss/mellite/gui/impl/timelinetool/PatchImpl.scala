/*
 *  PatchImpl.scala
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
package timelinetool

import java.awt.event.MouseEvent
import java.awt.geom.{Area, Ellipse2D}
import java.awt.image.BufferedImage
import java.awt.{Color, Cursor, Point, RenderingHints, Toolkit}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.desktop.Desktop
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.Edits
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.synth.proc.{Proc, Timeline}

object PatchImpl {
  private def mkImage(aa: Boolean): BufferedImage = {
    val img   = new BufferedImage(17, 17, BufferedImage.TYPE_INT_ARGB)
    val g     = img.createGraphics()
    val shp1  =   new Area(new Ellipse2D.Float(0, 0, 17, 17))
    shp1 subtract new Area(new Ellipse2D.Float(5, 5,  7,  7))
    val shp2  =   new Area(new Ellipse2D.Float(1, 1, 15, 15))
    shp2 subtract new Area(new Ellipse2D.Float(4, 4,  9,  9))
    if (aa) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setColor(Color.white)
    g.fill(shp1)
    g.setColor(Color.black)
    g.fill(shp2)
    g.dispose()
    img
  }

  lazy val image: BufferedImage = mkImage(aa = true)

  // anti-aliased transparency seem unsupported on Linux
  private lazy val cursor =
    Toolkit.getDefaultToolkit.createCustomCursor(mkImage(aa = Desktop.isMac), new Point(8, 8), "patch")
}
final class PatchImpl[S <: Sys[S]](protected val canvas: TimelineTrackCanvas[S])
  extends RegionImpl[S, TimelineTool.Patch[S]] with Dragging[S, TimelineTool.Patch[S]] {

  import TimelineTool.Patch

  def defaultCursor: Cursor = PatchImpl.cursor
  val name                  = "Patch"
  val icon: Icon            = GUI.iconNormal(Shapes.Patch)

  protected type Initial = ProcObjView.Timeline[S] // TimelineObjView[S]

  protected def dragToParam(d: Drag): Patch[S] = {
    val pos   = d.currentPos
    val sink  = canvas.findRegion(frame = pos, hitTrack = d.currentTrack) match {
      case Some(r: ProcObjView.Timeline[S]) if r != d.initial /* && r.inputs.nonEmpty */ =>  // region.inputs only carries linked ones!
        Patch.Linked(r)
      case _ =>
        Patch.Unlinked(frame = pos, y = d.currentEvent.getY)
    }
    Patch(d.initial, sink)
  }

  protected def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, region: TimelineObjView[S]): Unit =
    region match {
      case pv: ProcObjView.Timeline[S] => new Drag(e, hitTrack, pos, pv) // region.outputs only carries linked ones!
      case _ =>
    }

  protected def commitObj(drag: Patch[S])(span: SpanLikeObj[S], outObj: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] =
    (drag.sink, outObj) match {
      case (Patch.Linked(view), out: Proc[S]) =>
        val in = view.obj
        Edits.linkOrUnlink(out, in)

      case _ => None
    }
}
