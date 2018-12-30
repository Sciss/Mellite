/*
 *  CollectionLike.scala
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

import java.awt.event.{MouseAdapter, MouseEvent}

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{TimelineObjView, TimelineTool, TimelineTrackCanvas}
import de.sciss.model.impl.ModelImpl

import scala.swing.Component

/** A basic implementation block for timeline tools that process selected child views. */
trait CollectionLike[S <: Sys[S], A] extends TimelineTool[S, A] with ModelImpl[TimelineTool.Update[A]] {
  tool =>

  // protected def trackList: TrackList
  protected def canvas: TimelineTrackCanvas[S]

  /** Applies standard mouse selection techniques regarding regions.
    *
    * - If no modifier is hold, clicking outside of a region deselects all
    *   currently selected regions.
    * - Clicking on an already selected region has no effect.
    * - Clicking on a unselected region, will clear the selection and only select the new region.
    * - Holding shift while clicking will add or remove regions to the list of selected regions.
    */
  protected final def handleMouseSelection(e: MouseEvent, childOpt: Option[TimelineObjView[S]]): Unit = {
    val selMod = canvas.selectionModel
    if (e.isShiftDown) {
      childOpt.foreach { region =>
        if (selMod.contains(region)) {
          selMod -= region
        } else {
          selMod += region
        }
      }
    } else {
      if (!childOpt.exists(region => selMod.contains(region))) {
        // either hitting a region which wasn't selected, or hitting an empty area
        // --> deselect all
        selMod.clear()
        childOpt.foreach(selMod += _)
      }
    }
  }

  private val mia = new MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit = {
      e.getComponent.requestFocus()
      val pos       = canvas.screenToFrame(e.getX).toLong
      val hitTrack  = canvas.screenToTrack(e.getY)
      val childOpt  = canvas.findChildView(pos, hitTrack)  // procs span "two tracks". ouchilah...
      handlePress(e, hitTrack, pos, childOpt)
    }
  }

  /** Implemented by adding mouse (motion) listeners to the component. */
  final def install(component: Component): Unit = {
    component.peer.addMouseListener      (mia)
    component.peer.addMouseMotionListener(mia)
    component.cursor = defaultCursor
  }

  /** Implemented by removing listeners from component. */
  final def uninstall(component: Component): Unit = {
    component.peer.removeMouseListener      (mia)
    component.peer.removeMouseMotionListener(mia)
    component.cursor = null
  }

  /** Abstract method to be implemented by sub-classes. Called when the
    * mouse is pressed
    *
    * @param e          the event corresponding to the press
    * @param hitTrack   the track index corresponding to the vertical
    *                   mouse coordinate.
    * @param pos        the frame position corresponding to the horizontal
    *                   mouse coordinate
    * @param childOpt  `Some` timeline object that is beneath the mouse
    *                   position or `None` if the mouse is pressed over
    *                   an empty part of the timeline.
    */
  protected def handlePress(e: MouseEvent, hitTrack: Int, pos: Long,
                            childOpt: Option[TimelineObjView[S]]): Unit

  //  /** Method that is called when the mouse is released. Implemented
  //    * as a no-op, so only sub-classes that want to explicitly perform
  //    * actions need to override it.
  //    */
  //  protected def handleRelease(e: MouseEvent, hitTrack: Int, pos: Long,
  //                              regionOpt: Option[TimelineObjView[S]]): Unit = ()
}
