/*
 *  GraphemeCanvasImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.grapheme

import de.sciss.audiowidgets.Axis
import de.sciss.desktop.Implicits.DesktopComponent
import de.sciss.desktop.{FocusType, KeyStrokes, OptionPane}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.BasicTool.DragRubber
import de.sciss.mellite.GraphemeTool.EmptyRubber
import de.sciss.mellite.GraphemeTools.ToolChanged
import de.sciss.mellite.impl.TimelineCanvas2DImpl
import de.sciss.mellite.{DoubleSpan, GraphemeCanvas, GraphemeModel, GraphemeTools, ObjGraphemeView}
import de.sciss.numbers
import de.sciss.swingplus.Spinner

import javax.swing.{KeyStroke, SpinnerModel, SpinnerNumberModel}
import scala.math.{max, min, signum}
import scala.swing.event.{Key, MousePressed}
import scala.swing.{Action, BoxPanel, Component, Orientation, Swing}

trait GraphemeCanvasImpl[T <: Txn[T]] extends TimelineCanvas2DImpl[T, Double, ObjGraphemeView[T]]
  with GraphemeCanvas[T] {

  def graphemeModel: GraphemeModel

  final val graphemeTools: GraphemeTools[T] = GraphemeTools(this)

  protected def emptyRubber: DragRubber[Double] = EmptyRubber

  def selectionModel: ObjGraphemeView.SelectionModel[T]

  // def intersect(span: Span): Iterator[TimelineObjView[T]]

//  def findChildView(frame: Long): Option[GraphemeObjView[T]]

  // def findViews(r: TrackTool.Rectangular): Iterator[GraphemeObjView[T]]

  // ---- impl ----

  private[this] val _yAxis = {
    val res = new Axis(Orientation.Vertical)
    res.maximum     = 1.0
//    res.fixedBounds = true
    res
  }

  def yAxis: Axis = _yAxis

  final def screenToModelPos(y: Int): Double = {
    val yr  = graphemeModel.yAxisRange
    val lo  = yr.start
    val hi  = yr.stop
    val hm  = _yAxis.peer.getHeight - 1
    import numbers.Implicits._
    y.linLin(0, hm, hi, lo) // note 'hi to lo'
  }

  override def screenToModelPosF(y: Int): Double = screenToModelPos(y)

  final def screenToModelExtent(y: Int): Double = {
    val yr  = graphemeModel.yAxisRange
    val lo  = yr.start
    val hi  = yr.stop
    val hm  = _yAxis.peer.getHeight - 1
    import numbers.Implicits._
    y.linLin(0, hm, lo, hi) // note 'lo to hi'
  }

  override def screenToModelExtentF(dy: Int): Double = screenToModelExtent(dy)

  final def modelPosToScreen(m: Double): Double = {
    val yr  = graphemeModel.yAxisRange
    val lo  = yr.start
    val hi  = yr.stop
    val hm  = _yAxis.peer.getHeight - 1
    import numbers.Implicits._
    m.linLin(hi, lo, 0, hm) // note 'hi to lo'
  }

  final def modelExtentToScreen(m: Double): Double = {
    val yr  = graphemeModel.yAxisRange
    val lo  = yr.start
    val hi  = yr.stop
    val hm  = _yAxis.peer.getHeight - 1
    import numbers.Implicits._
    m.linLin(lo, hi, 0, hm) // note 'lo to hi'
  }

  final def modelYBox(a: Double, b: Double): (Double, Double) = if (a < b) (a, b - a) else (b, a - b)

  // constructor
  {
    graphemeTools.addListener {
      case ToolChanged(ch) =>
        ch.before.removeListener(toolListener)
        ch.now   .addListener   (toolListener)
    }
    graphemeTools.currentTool.addListener(toolListener)

    import FocusType.{Window => Focus}
    import KeyStrokes.ctrl
    graphemeModel.modifiableOption.foreach { gmm =>
      _yAxis.addAction("yaxis-zoom-in" , new ActionZoomY(gmm, factor = 0.5,  ctrl + Key.Up  ), Focus)
      _yAxis.addAction("yaxis-zoom-out", new ActionZoomY(gmm, factor = 2.0,  ctrl + Key.Down), Focus)
      _yAxis.listenTo(_yAxis.mouse.clicks)
      _yAxis.reactions += {
        case m: MousePressed =>
          if (m.clicks == 2) showYAxisRange(gmm)
      }
    }
    graphemeModel.addListener {
      case GraphemeModel.YAxisRange(_, ch) =>
        val yr = ch.now
        setYAxis(yr.start, yr.stop)
    }
  }

  private def showYAxisRange(gmm: GraphemeModel.Modifiable): Unit = {
    val yr0   = gmm.yAxisRange
    val hi0   = yr0.stop
    val lo0   = yr0.start
    val mHi   = new SpinnerNumberModel(hi0, min(hi0, -1.0e6), max(hi0, 1.0e6), 0.01)
    val mLo   = new SpinnerNumberModel(lo0, min(lo0, -1.0e6), max(lo0, 1.0e6), 0.01)

    def mkSpinner(m: SpinnerModel): Spinner = {
      val res = new Spinner(m)
//      res.listenTo(res)
//      res.reactions += {
//        case ValueChanged(_) =>
//      }
      res
    }

    val ggHi: Component = mkSpinner(mHi)
    val ggLo: Component = mkSpinner(mLo)

    val pane = new BoxPanel(Orientation.Vertical) {
//      contents += butPane
//      contents += Swing.VStrut(8)
      contents += ggHi
      contents += Swing.VStrut(4)
      contents += ggLo
      contents += Swing.VStrut(12)
    }

    val opt = OptionPane.confirmation(message = pane, messageType = OptionPane.Message.Question,
      optionType = OptionPane.Options.OkCancel, focus = Some(ggHi /*.textField*/))
    val res = opt.show(None, title = "Adjust Y Axis")  // XXX TODO - find window
    if (res == OptionPane.Result.Ok) {
      val hi1 = mHi.getNumber.doubleValue()
      val lo1 = mLo.getNumber.doubleValue()
      if (hi1 != lo1) gmm.yAxisRange = DoubleSpan(lo1, hi1)
    }
  }

  private def setYAxis(lo: Double, hi: Double): Unit = {
    val a = _yAxis
    if (lo < hi) {
      a.inverted  = false
      a.minimum   = lo
      a.maximum   = hi
    } else {
      a.inverted  = true
      a.minimum   = hi
      a.maximum   = lo
    }
    canvasComponent.repaint()
  }

  private class ActionZoomY(gmm: GraphemeModel.Modifiable, factor: Double, stroke: KeyStroke)
    extends Action(s"Zoom vertical by $factor") {

    accelerator = Some(stroke)

    override def apply(): Unit = {
      val yr0   = gmm.yAxisRange
      val lo0   = yr0.start
      val hi0   = yr0.stop
      val bi    = signum(lo0) == -signum(hi0)
      val lo1  = if (bi) lo0 * factor else lo0
      val hi1  = hi0 * factor
      if (lo1 != hi1) gmm.yAxisRange = DoubleSpan(lo1, hi1)
    }
  }
}