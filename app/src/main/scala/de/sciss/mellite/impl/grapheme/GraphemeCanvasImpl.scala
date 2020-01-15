/*
 *  GraphemeCanvasImpl.scala
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

package de.sciss.mellite.impl.grapheme

import de.sciss.audiowidgets.Axis
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.mellite.GraphemeTool.EmptyRubber
import de.sciss.mellite.GraphemeTools.ToolChanged
import de.sciss.mellite.impl.TimelineCanvas2DImpl
import de.sciss.mellite.{GraphemeCanvas, GraphemeTools, ObjGraphemeView}
import de.sciss.numbers

import scala.swing.Orientation

trait GraphemeCanvasImpl[S <: Sys[S]] extends TimelineCanvas2DImpl[S, Double, ObjGraphemeView[S]]
  with GraphemeCanvas[S] {

  final val graphemeTools: GraphemeTools[S] = GraphemeTools(this)

  protected def emptyRubber: DragRubber[Double] = EmptyRubber

  def selectionModel: ObjGraphemeView.SelectionModel[S]

  // def intersect(span: Span): Iterator[TimelineObjView[S]]

//  def findChildView(frame: Long): Option[GraphemeObjView[S]]

  // def findViews(r: TrackTool.Rectangular): Iterator[GraphemeObjView[S]]

  // ---- impl ----

  private[this] val _yAxis = {
    val res = new Axis(Orientation.Vertical)
    res.maximum     = 1.0
//    res.fixedBounds = true
    res
  }

  def yAxis: Axis = _yAxis

  final def screenToModelPos(y: Int): Double = {
    val a   = _yAxis
    val min = a.minimum
    val max = a.maximum
    val hm  = a.peer.getHeight - 1
    import numbers.Implicits._
    y.linLin(0, hm, max, min) // note 'max to min'
  }

  final def screenToModelExtent(y: Int): Double = {
    val a   = _yAxis
    val min = a.minimum
    val max = a.maximum
    val hm  = a.peer.getHeight - 1
    import numbers.Implicits._
    y.linLin(0, hm, min, max) // note 'min to max'
  }

  final def modelPosToScreen(m: Double): Double = {
    val a   = _yAxis
    val min = a.minimum
    val max = a.maximum
    val hm  = a.peer.getHeight - 1
    import numbers.Implicits._
    m.linLin(max, min, 0, hm) // note 'max to min'
  }

  final def modelExtentToScreen(m: Double): Double = {
    val a   = _yAxis
    val min = a.minimum
    val max = a.maximum
    val hm  = a.peer.getHeight - 1
    import numbers.Implicits._
    m.linLin(min, max, 0, hm) // note 'min to max'
  }

  final def modelYBox(a: Double, b: Double): (Double, Double) = if (a < b) (a, b - a) else (b, a - b)

  graphemeTools.addListener {
    case ToolChanged(change) =>
      change.before.removeListener(toolListener)
      change.now   .addListener   (toolListener)
  }
  graphemeTools.currentTool.addListener(toolListener)
}