/*
 *  RubberBand.scala
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

import java.awt.event.{KeyEvent, KeyListener, MouseEvent}

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.BasicTools
import de.sciss.mellite.gui.GraphemeTool.{DragCancel, DragEnd, DragRubber}
import de.sciss.span.Span
import javax.swing.event.MouseInputAdapter

// XXX TODO --- DRY with Dragging and timeline.tool.RubberBand
trait RubberBand[S <: Sys[S], A] {
  _: CollectionLike[S, A] =>

  final protected def mkRubber(e: MouseEvent, modelY: Double, pos: Long): Unit =
    new Rubber(e, firstModelY = modelY, firstPos = pos)

  private[this] class Rubber(val firstEvent: MouseEvent, val firstModelY: Double, val firstPos: Long)
    extends MouseInputAdapter with KeyListener {

    private var started         = false
    private var _currentEvent   = firstEvent
    private var _currentModelY  = firstModelY
    private var _currentPos     = firstPos

    def currentEvent  : MouseEvent  = _currentEvent
    def currentModelY : Double      = _currentModelY
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
      _currentEvent   = e
      _currentPos     = canvas.screenToFrame(e.getX).toLong
      _currentModelY  = canvas.screenYToModel(e.getY) // - firstEvent.getY) + canvas.screenToTrack(firstEvent.getY)
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
      val dModelYOff  = math.min(firstModelY, currentModelY)
      val dModelYExt  = math.max(firstModelY, currentModelY) - dModelYOff + 1

      val rubber  = DragRubber(modelYOffset = dModelYOff, modelYExtent = dModelYExt, span = Span(dStart, dStop))
      dispatch(rubber)

      val regions = canvas.findChildViews(rubber).toSet
      val selMod  = canvas.selectionModel
      // XXX TODO --- not very efficient
      val toRemove  = selMod.iterator.filter(!regions.contains(_))
      val toAdd     = regions        .filter(!selMod .contains(_))
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
