/*
 *  DraggingTool.scala
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

import java.awt.event.{KeyEvent, KeyListener, MouseEvent}

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.BasicTool.{DragAdjust, DragBegin, DragCancel, DragEnd}
import javax.swing.event.MouseInputAdapter

/** A mixin trait for region-like timeline tools that enables updates during mouse dragging.
  * It adds an internal class `Drag` that embodies that dragging state (initial
  * and current positions). Dragging is useful for all parameters that can
  * be continuously changed such as region position but also region gain. It does
  * not necessarily mean that regions are moved. In other words, whenever the
  * `mouseDragged` event causes a meaningful change in the editing state.
  *
  * Custom data can be added by the sub-class by specifying the type member `Initial`.
  *
  * All the sub-class must do is call `new Drag` and provide the body of method `dragToParam`.
  */
trait DraggingTool[S <: Sys[S], A, Y] {
  _: CollectionToolLike[S, A, Y, _] =>

  protected def dragToParam(d: Drag): A

  protected type Initial

  final protected var currentParam = Option.empty[A]

  final protected def dragEnd   ()       : Unit = dispatch(DragEnd   )
  final protected def dragCancel(d: Drag): Unit = dispatch(DragCancel)

  /** Determines if the drag operations should be started or not.
    * The default behavior is to wait until the mouse is dragged
    * by around four pixels. Sub-classes may override this, for
    * example to have the drag start immediately without threshold.
    *
    * @return `true` if the parameter data signalize that a drag has started,
    *         `false` if it is not (yet) sufficient.
    */
  protected def dragStarted(d: this.Drag): Boolean =
    d.currentEvent.getPoint.distanceSq(d.firstEvent.getPoint) > 16

  final protected def dragBegin(d: Drag): Unit = {
    val p = dragToParam(d)
    currentParam = Some(p)
    dispatch(DragBegin)
    dispatch(DragAdjust(p))
  }

  final protected def dragAdjust(d: Drag): Unit =
    currentParam.foreach { oldP =>
      val p = dragToParam(d)
      if (p != oldP) {
        currentParam = Some(p)
        dispatch(DragAdjust(p))
      }
    }

  /** Objects that represents a (potential) drag. When instantiated,
    * it installs itself on the parent component of `firstEvent` and
    * automatically removes itself when the mouse is released.
    *
    * A drag is only formally started once `dragStarted` returns `true`.
    * It will then update the drag state by calling repeatedly into
    * `dragToParam` and dispatching appropriate events.
    *
    * A drag can be aborted by pressing the <tt>Escape</tt> key.
    */
  protected class Drag(val firstEvent: MouseEvent, val firstModelY: Y,
                       val firstPos: Long, val initial: Initial)
    extends MouseInputAdapter with KeyListener {

    private[this] var started         = false
    private[this] var _currentEvent   = firstEvent
    private[this] var _currentModelY  = firstModelY
    private[this] var _currentPos     = firstPos

    def currentEvent  : MouseEvent  = _currentEvent
    def currentModelY : Y           = _currentModelY
    def currentPos    : Long        = _currentPos

    // ---- constructor ----
    {
      val comp = firstEvent.getComponent
      comp.addMouseListener(this)
      comp.addMouseMotionListener(this)
      comp.requestFocus() // (why? needed to receive key events?)
    }

    override def mouseReleased(e: MouseEvent): Unit = {
      unregister()
      if (started) dragEnd()
    }

    private def unregister(): Unit = {
      val comp = firstEvent.getComponent
      comp.removeMouseListener      (this)
      comp.removeMouseMotionListener(this)
      comp.removeKeyListener        (this)
    }

    private def calcCurrent(e: MouseEvent): Unit = {
      _currentEvent   = e
      _currentPos     = canvas.screenToFrame(e.getX).toLong
      _currentModelY  = canvas.screenToModelPos(e.getY)
    }

    override def mouseDragged(e: MouseEvent): Unit = {
      calcCurrent(e)
      if (!started) {
        started = dragStarted(this)
        if (!started) return
        e.getComponent.addKeyListener(this)
        dragBegin(this)
      }
      dragAdjust(this)
    }

    def keyPressed(e: KeyEvent): Unit =
      if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
        unregister()
        dragCancel(this)
      }

    def keyTyped   (e: KeyEvent): Unit = ()
    def keyReleased(e: KeyEvent): Unit = ()
  }
}
