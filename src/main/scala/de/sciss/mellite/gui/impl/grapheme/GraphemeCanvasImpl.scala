/*
 *  GraphemeCanvasImpl.scala
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

package de.sciss.mellite
package gui
package impl
package grapheme

import de.sciss.audiowidgets.Axis
import de.sciss.audiowidgets.impl.TimelineCanvasImpl
import de.sciss.lucre.synth.Sys
import de.sciss.numbers

import scala.swing.Orientation

trait GraphemeCanvasImpl[S <: Sys[S]] extends TimelineCanvasImpl with GraphemeCanvas[S] {
  final val graphemeTools: GraphemeTools[S] = GraphemeTools(this)

  def selectionModel: GraphemeObjView.SelectionModel[S]

  // def intersect(span: Span): Iterator[TimelineObjView[S]]

  def findChildView(frame: Long): Option[GraphemeObjView[S]]

  // def findViews(r: TrackTool.Rectangular): Iterator[GraphemeObjView[S]]

  // ---- impl ----

  private[this] val _yAxis = {
    val res = new Axis(Orientation.Vertical)
    res.maximum     = 1.0
//    res.fixedBounds = true
    res
  }

  def yAxis: Axis = _yAxis

  final def screenToModelY(y: Int): Double = {
    val a   = _yAxis
    val min = a.minimum
    val max = a.maximum
    import numbers.Implicits._
    y.linLin(0, a.peer.getHeight - 1, max, min)
  }

  final def modelYToScreen(m: Double): Int = {
    val a   = _yAxis
    val min = a.minimum
    val max = a.maximum
    import numbers.Implicits._
    math.round(m.linLin(max, min, 0, a.peer.getHeight - 1)).toInt
  }

  final def modelYBox(a: Double, b: Double): (Double, Double) = if (a < b) (a, b - a) else (b, a - b)

  private[this] val selectionListener: SelectionModel.Listener[S, GraphemeObjView[S]] = {
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
}