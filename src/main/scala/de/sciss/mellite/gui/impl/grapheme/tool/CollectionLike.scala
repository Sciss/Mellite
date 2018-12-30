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

package de.sciss.mellite.gui.impl.grapheme.tool

import java.awt.event.{MouseAdapter, MouseEvent}

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeTool, GraphemeCanvas}
import de.sciss.model.impl.ModelImpl

import scala.swing.Component

/** A basic implementation block for grapheme tools that process selected child views. */
trait CollectionLike[S <: Sys[S], A] extends GraphemeTool[S, A] with ModelImpl[GraphemeTool.Update[A]] {
  tool =>

  // XXX TODO DRY with timeline.tool.CollectionLike

  // protected def trackList: TrackList
  protected def canvas: GraphemeCanvas[S]

  /** Applies standard mouse selection techniques regarding marks.
    *
    * - If no modifier is hold, clicking outside of a mark deselects all
    *   currently selected marks.
    * - Clicking on an already selected mark has no effect.
    * - Clicking on a unselected mark, will clear the selection and only select the new mark.
    * - Holding shift while clicking will add or remove marks to the list of selected marks.
    */
  protected final def handleMouseSelection(e: MouseEvent, childOpt: Option[GraphemeObjView[S]]): Unit = {
    val selMod = canvas.selectionModel
    if (e.isShiftDown) {
      childOpt.foreach { mark =>
        if (selMod.contains(mark)) {
          selMod -= mark
        } else {
          selMod += mark
        }
      }
    } else {
      if (!childOpt.exists(mark => selMod.contains(mark))) {
        // either hitting a mark which wasn't selected, or hitting an empty area
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
      val modelY    = canvas.screenYToModel(e.getY)
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
    * @param e        the event corresponding to the press
    * @param modelY   the model position corresponding to the vertical mouse coordinate.
    * @param pos      the frame position corresponding to the horizontal mouse coordinate
    * @param childOpt  `Some` grapheme object that is beneath the mouse
    *                 position or `None` if the mouse is pressed over
    *                 an empty part of the grapheme.
    */
  protected def handlePress(e: MouseEvent, modelY: Double, pos: Long,
                            childOpt: Option[GraphemeObjView[S]]): Unit

  //  /** Method that is called when the mouse is released. Implemented
  //    * as a no-op, so only sub-classes that want to explicitly perform
  //    * actions need to override it.
  //    */
  //  protected def handleRelease(e: MouseEvent, hitTrack: Int, pos: Long,
  //                              markOpt: Option[GraphemeObjView[S]]): Unit = ()
}
