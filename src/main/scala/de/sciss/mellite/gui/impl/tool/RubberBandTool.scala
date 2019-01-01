/*
 *  RubberBandTool.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
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
import de.sciss.mellite.gui.BasicTool.{DragCancel, DragEnd, DragRubber}
import de.sciss.mellite.gui.BasicTools
import de.sciss.span.Span
import javax.swing.event.MouseInputAdapter

// XXX TODO --- DRY with DraggingTool
trait RubberBandTool[S <: Sys[S], A, Y, Child] {
  _: CollectionToolLike[S, A, Y, Child] =>

  final protected def mkRubber(e: MouseEvent, modelY: Y, pos: Long): Unit =
    new Rubber(e, firstModelY = modelY, firstPos = pos)

  private[this] class Rubber(val firstEvent: MouseEvent, val firstModelY: Y, val firstPos: Long)
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
      if (started) dispatch(DragEnd)
    }

    private def unregister(): Unit = {
      val comp = firstEvent.getComponent
      comp.removeMouseListener      (this)
      comp.removeMouseMotionListener(this)
      comp.removeKeyListener        (this)
    }

    private def calcCurrent(e: MouseEvent): Unit = {
      _currentEvent = e
      _currentPos   = canvas.screenToFrame(e.getX).toLong
      _currentModelY = canvas.screenToModelPos(e.getY) // - firstEvent.getY) + canvas.screenToTrack(firstEvent.getY)
    }

    override def mouseDragged(e: MouseEvent): Unit = {
      calcCurrent(e)
      if (!started) {
        started = currentEvent.getPoint.distanceSq(firstEvent.getPoint) > 16
        if (!started) return
        e.getComponent.addKeyListener(this)
        // dispatch(DragBegin)
      }

      val dStart      = math.min(firstPos, currentPos)
      val dStop       = math.max(dStart + BasicTools.MinDur, math.max(firstPos, currentPos))
      val (dModelYOff, dModelYExt) = canvas.modelYBox(firstModelY, currentModelY)

      val rubber  = DragRubber[Y](modelYOffset = dModelYOff, modelYExtent = dModelYExt, span = Span(dStart, dStop),
        isValid = true)
      dispatch(rubber)

      val regions = canvas.findChildViews(rubber).toSet
      val selMod  = canvas.selectionModel
      // XXX TODO --- not very efficient
      val toRemove  = selMod.iterator.filter(child => !regions.contains(child))
      val toAdd     = regions        .filter(child => !selMod .contains(child))
      toRemove.foreach(selMod -= _)
      toAdd   .foreach(selMod += _)
    }

    def keyPressed(e: KeyEvent): Unit =
      if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
        unregister()
        dispatch(DragCancel)
      }

    def keyTyped   (e: KeyEvent): Unit = ()
    def keyReleased(e: KeyEvent): Unit = ()
  }
}
