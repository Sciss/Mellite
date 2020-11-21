/*
 *  CollectionToolLike.scala
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

package de.sciss.mellite.impl.tool

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{BasicTool, TimelineCanvas2D}
import de.sciss.model.impl.ModelImpl

import java.awt.Cursor
import scala.swing.Component

/** A basic implementation block for timeline tools that process selected child views. */
trait CollectionToolLike[T <: Txn[T], A, Y, Child] extends BasicTool[T, A] with ModelImpl[BasicTool.Update[A]] {
  tool =>

  type C = Child

  // protected def trackList: TrackList
  protected def canvas: TimelineCanvas2D[T, Y, Child]

  private[this] var lastCursor: awt.Cursor = _

  protected val hover: Boolean = false

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

  private[this] val mia = new MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit = {
      e.getComponent.requestFocus()
      processMouse(e)(handlePress)
    }

    override def mouseEntered(e: MouseEvent): Unit = mouseHover(e)
    override def mouseMoved  (e: MouseEvent): Unit = mouseHover(e)

    private  def mouseHover  (e: MouseEvent): Unit = if (hover) processMouse(e)(handleHover)

    override def mouseDragged(e: MouseEvent): Unit = if (hover) processMouse(e)(handleDrag )

    override def mouseReleased(e: MouseEvent): Unit = processMouse(e)(handleRelease)

    private def processMouse[A](e: MouseEvent)(fun: (MouseEvent, Long, Y, Option[C]) => A): A = {
      val pos       = canvas.screenToFrame(e.getX).toLong
      val modelY    = canvas.screenToModelPos(e.getY)
      val childOpt  = canvas.findChildView(pos, modelY)
      fun(e, pos, modelY, childOpt)
    }
  }

  /** Implemented by adding mouse input listeners to the component. */
  final def install(component: Component, e: Option[MouseEvent]): Unit = {
    // println(s"install $this. hover? $hover")
    component           .peer.addMouseListener      (mia)
    if (hover) component.peer.addMouseMotionListener(mia)
    val csr           = getCursor(e)
    lastCursor        = csr
    component.cursor  = csr
  }

  def getCursor(eOpt: Option[MouseEvent]): Cursor = eOpt match {
    case None => defaultCursor
    case Some(e) =>
      val pos       = canvas.screenToFrame(e.getX).toLong
      val modelY    = canvas.screenToModelPos(e.getY)
      val childOpt  = canvas.findChildView(pos, modelY)
      getCursor(e, modelY, pos, childOpt)
  }

  protected def defaultCursor: awt.Cursor = null

  protected def getCursor(e: MouseEvent, modelY: Y, pos: Long, childOpt: Option[Child]): awt.Cursor =
    defaultCursor

  /** Implemented by removing listeners from component. */
  final def uninstall(component: Component): Unit = {
    component           .peer.removeMouseListener      (mia)
    if (hover) component.peer.removeMouseMotionListener(mia)
    component.cursor  = null
    lastCursor        = null
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
  protected def handlePress(e: MouseEvent, pos: Long, modelY: Y, childOpt: Option[Child]): Unit

  protected def handleRelease(e: MouseEvent, pos: Long, modelY: Y, childOpt: Option[Child]): Unit = ()

  protected def handleDrag(e: MouseEvent, pos: Long, modelY: Y, childOpt: Option[Child]): Unit = ()

  protected def handleHover(e: MouseEvent, pos: Long, modelY: Y, childOpt: Option[Child]): Unit = {
    val csr = getCursor(e, modelY, pos, childOpt)
    if (lastCursor != csr) {
      lastCursor = csr
      e.getComponent.setCursor(csr)
    }
  }
}
