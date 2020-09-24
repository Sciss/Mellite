/*
 *  TimelineCanvas2DImpl.scala
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

package de.sciss.mellite.impl

import java.awt.{BasicStroke, Color, Graphics2D, Stroke}

import de.sciss.audiowidgets.impl.TimelineCanvasImpl
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.BasicTool.{DragAdjust, DragCancel, DragEnd, DragRubber}
import de.sciss.mellite.Mellite.log
import de.sciss.mellite.TimelineTool.Update
import de.sciss.mellite.{BasicTool, GUI, SelectionModel, TimelineCanvas2D}
import de.sciss.model.Model
import de.sciss.span.Span

object TimelineCanvas2DImpl {
  final val strokeRubber: Stroke = new BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
    Array[Float](3f, 5f), 0f)

  final val strokeDropRegion: Stroke = new BasicStroke(3f)

  final val colorDropRegionBg: Color =
    new Color(if (GUI.isDarkSkin) 0x7FFFFFFF else 0x7F000000, true)
}
trait TimelineCanvas2DImpl[T <: Txn[T], Y, Child]
  extends TimelineCanvasImpl
    with TimelineCanvas2D[T, Y, Child] {

  import TimelineCanvas2DImpl._

  // ---- abstract ----

  protected var toolState: Option[Any]

  protected def emptyRubber: DragRubber[Y]

  // ---- impl ----

  protected final var rubberState: DragRubber[Y] = emptyRubber

  protected final val toolListener: Model.Listener[Update[Any]] = {
    // case TrackTool.DragBegin =>
    case DragCancel =>
      log(s"Drag cancel $toolState")
      if (toolState.isDefined) {
        toolState   = None
        repaint()
      } else if (rubberState.isValid) {
        rubberState = emptyRubber
        repaint()
      }

    case DragEnd =>
      log(s"Drag end $toolState")
      toolState.fold[Unit] {
        if (rubberState.isValid) {
          rubberState = emptyRubber
          repaint()
        }
      } { state =>
        toolState   = None
        rubberState = emptyRubber
        commitToolChanges(state)
        repaint()
      }

    case DragAdjust(value) =>
      // log(s"Drag adjust $value")
      val some = Some(value)
      if (toolState != some) {
        toolState = some
        repaint()
      }

    case BasicTool.Adjust(state) =>
      log(s"Tool commit $state")
      toolState = None
      commitToolChanges(state)
      repaint()

    case state: DragRubber[_] =>
      log(s"Tool rubber $state")
      rubberState = state.asInstanceOf[DragRubber[Y]]    // XXX TODO not pretty
      repaint()
  }

  private[this] val selectionListener: SelectionModel.Listener[T, Child] = {
    case SelectionModel.Update(_ /* added */, _ /* removed */) =>
      canvasComponent.repaint() // XXX TODO: dirty rectangle optimization
  }

  override protected def componentShown(): Unit = {
    super.componentShown()
    selectionModel.addListener(selectionListener)
  }

  override protected def componentHidden(): Unit = {
    super.componentHidden()
    selectionModel.removeListener(selectionListener)
  }

  protected def commitToolChanges(value: Any): Unit

  protected def drawDropFrame(g: Graphics2D, modelYStart: Y, modelYStop: Y, span: Span,
                              rubber: Boolean): Unit = {
    val x1  = frameToScreen(span.start ).toInt
    val x2  = frameToScreen(span.stop  ).toInt
    g.setColor(colorDropRegionBg)
    val strokeOrig = g.getStroke
    g.setStroke(if (rubber) strokeRubber else strokeDropRegion)
    val y1  = modelPosToScreen(modelYStart).toInt
    val y2  = modelPosToScreen(modelYStop ).toInt
    // XXX TODO ; what are the +1, -1 for? to compensate for stroke width?
    val x1b = math.min(x1 + 1, x2)
    val x2b = math.max(x1b, x2 - 1)
    val y1b = math.min(y1 + 1, y2)
    val y2b = math.max(y1, y2 - 1)  // not y1b!
    g.drawRect(x1b, y1b, x2b - x1b, y2b - y1b)
    g.setStroke(strokeOrig)
  }
}
