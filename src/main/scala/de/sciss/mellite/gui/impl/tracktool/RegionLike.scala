/*
 *  RegionLike.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package tracktool

import de.sciss.synth.proc.Sys
import java.awt.event.{MouseAdapter, MouseEvent}
import scala.swing.Component
import de.sciss.model.impl.ModelImpl

trait RegionLike[S <: Sys[S], A] extends TrackTool[S, A] with ModelImpl[TrackTool.Update[A]] {
  tool =>

  // protected def trackList: TrackList
  protected def canvas: TimelineProcCanvas[S]

  /** Applies standard mouse selection techniques regarding regions. */
  protected final def handleMouseSelection(e: MouseEvent, regionOpt: Option[timeline.ProcView[S]]): Unit = {
    val selm = canvas.selectionModel
    if (e.isShiftDown) {
      regionOpt.foreach { region =>
        if (selm.contains(region)) {
          selm -= region
        } else {
          selm += region
        }
      }
    } else {
      if (regionOpt.map(region => !selm.contains(region)) getOrElse true) {
        // either hitten a region which wasn't selected, or hitting an empty area
        // --> deselect all
        selm.clear()
        regionOpt.foreach(selm += _)
      }
    }
  }

  private val mia = new MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit = {
      val pos       = canvas.screenToFrame(e.getX).toLong
      val hitTrack  = canvas.screenToTrack(e.getY)
      val regionOpt = canvas.findRegion(pos, hitTrack)  // procs span "two tracks". ouchilah...
      handlePress(e, hitTrack, pos, regionOpt)
    }
  }

  final def uninstall(component: Component): Unit = {
    component.peer.removeMouseListener      (mia)
    component.peer.removeMouseMotionListener(mia)
    component.cursor = null
  }

  final def install(component: Component): Unit = {
    component.peer.addMouseListener      (mia)
    component.peer.addMouseMotionListener(mia)
    component.cursor = defaultCursor
  }

  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long,
                            regionOpt: Option[timeline.ProcView[S]]): Unit
}