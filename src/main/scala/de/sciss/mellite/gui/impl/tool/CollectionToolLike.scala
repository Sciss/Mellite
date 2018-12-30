/*
 *  CollectionToolLike.scala
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

package de.sciss.mellite.gui.impl.tool

import java.awt.event.{MouseAdapter, MouseEvent}

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{BasicTool, TimelineCanvas2D}
import de.sciss.model.impl.ModelImpl

import scala.swing.Component

/** A basic implementation block for timeline tools that process selected child views. */
trait CollectionToolLike[S <: Sys[S], A, Y, Child] extends BasicTool[S, A] with ModelImpl[BasicTool.Update[A]] {
  tool =>

  // protected def trackList: TrackList
  protected def canvas: TimelineCanvas2D[S, Y, Child]

  /** Applies standard mouse selection techniques regarding regions.
    *
    * - If no modifier is hold, clicking outside of a region deselects all
    *   currently selected regions.
    * - Clicking on an already selected region has no effect.
    * - Clicking on a unselected region, will clear the selection and only select the new region.
    * - Holding shift while clicking will add or remove regions to the list of selected regions.
    */
  protected final def handleMouseSelection(e: MouseEvent, childOpt: Option[Child]): Unit = {
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
      val modelY    = canvas.screenToModelY(e.getY)
      val childOpt  = canvas.findChildView(pos, modelY)
      handlePress(e, modelY, pos, childOpt)
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
    * @param modelY     the model position corresponding to the vertical
    *                   mouse coordinate.
    * @param pos        the frame position corresponding to the horizontal
    *                   mouse coordinate
    * @param childOpt  `Some` timeline object that is beneath the mouse
    *                   position or `None` if the mouse is pressed over
    *                   an empty part of the timeline.
    */
  protected def handlePress(e: MouseEvent, modelY: Y, pos: Long,
                            childOpt: Option[Child]): Unit

  //  /** Method that is called when the mouse is released. Implemented
  //    * as a no-op, so only sub-classes that want to explicitly perform
  //    * actions need to override it.
  //    */
  //  protected def handleRelease(e: MouseEvent, hitTrack: Int, pos: Long,
  //                              regionOpt: Option[Child]): Unit = ()
}
