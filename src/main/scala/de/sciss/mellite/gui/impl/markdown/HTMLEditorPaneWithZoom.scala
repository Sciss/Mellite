/*
 *  HTMLEditorPaneWithZoom.scala
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

package de.sciss.mellite.gui.impl.markdown

import java.awt.{Graphics, Graphics2D, Shape}

import de.sciss.swingplus.EditorPane
import javax.swing.JEditorPane
import javax.swing.text.html.{HTML, HTMLEditorKit}
import javax.swing.text.{AbstractDocument, Element, Position, StyleConstants, View, ViewFactory, html}


/** An editor pane for HTML content that allows zooming.
  *
  * This is a hack following https://stackoverflow.com/questions/680817/ .
  * It has a number of glitches, most notably text overshoots with certain
  * zoom factors (e.g. 1.25) when the view width becomes large, and at
  * small zoom factors (e.g. 0.5) the width is not filled.
  *
  * So this needs more work in the future.
  */
class HTMLEditorPaneWithZoom(text0: String) extends EditorPane("text/html", text0) {
  private def contentType0 = "text/html"

  override lazy val peer: JEditorPane = new JEditorPane with SuperMixin {
    setEditorKitForContentType(contentType0, Kit)
    setContentType(contentType0)
    setText(text0)
  }

  private[this] var _zoomFactor = 1.0

  def zoom: Double = _zoomFactor

  def zoom_=(value: Double): Unit =
    if (_zoomFactor != value) {
      _zoomFactor = value
      repaint()
    }

  private object Kit extends HTMLEditorKit {
    override def getViewFactory: ViewFactory = FactoryWithZoom

    private object FactoryWithZoom extends HTMLEditorKit.HTMLFactory {
      override def create(elem: Element): View = {
        val attrs     = elem.getAttributes
        val elemName  = attrs.getAttribute(AbstractDocument.ElementNameAttribute)

        if (elemName != null) super.create(elem)
        else attrs.getAttribute(StyleConstants.NameAttribute) match {
          case HTML.Tag.HTML    => new BlockViewWithZoom(elem)
//          case HTML.Tag.IMPLIED =>
//              val ws = elem.getAttributes.getAttribute(CSS.Attribute.WHITE_SPACE)
//              if (ws == "pre") super.create(elem)
//              else new ParagraphViewWithZoom(elem)
//
//          // paragraph
//          case HTML.Tag.P | HTML.Tag.H1 | HTML.Tag.H2 | HTML.Tag.H3 | HTML.Tag.H4 | HTML.Tag.H5 | HTML.Tag.H6 | HTML.Tag.DT =>
//            new ParagraphViewWithZoom(elem)

          case _ => super.create(elem)
        }
      }
    }


    private class BlockViewWithZoom(elem: Element) extends html.BlockView(elem, View.Y_AXIS) {
      override protected def layout(width: Int, height: Int): Unit =
        if (width < Integer.MAX_VALUE) {
          val scale   = _zoomFactor
//          val scaleR  = math.pow(1.0 / scale, 0.98)
          val scaleR  = 1.0 / scale
//          val pad     = if (scaleR >= 1.0) 1.0 else math.pow(scale, 0.25)    // why are we seeing overshoot otherwise?
//          val ws      = math.max(1, (width  * scaleR * pad).toInt)
//          val hs      = math.max(1, (height * scaleR * pad).toInt)
          val pad     = if (scale == 1.0) 0 else if (scale < 1.0) 48 else -48  // why are we seeing overshoot otherwise?
          val ws      = math.max(1, (width  * scaleR).toInt + pad)
          val hs      = math.max(1, (height * scaleR).toInt + pad)
//          println(s"layout($ws, $hs)")
          super.layout(ws, hs)
        }

      override def paint(g: Graphics, allocation: Shape): Unit = {
        val g2    = g.asInstanceOf[Graphics2D]
        val scale = _zoomFactor
//        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        val atOld = g2.getTransform
        g2.scale(scale, scale)
        super.paint(g2, allocation)
        g2.setTransform(atOld)
      }

      override def getMinimumSpan(axis: Int): Float = {
        val f = super.getMinimumSpan(axis)
        (f * _zoomFactor).toFloat
      }

      override def getMaximumSpan(axis: Int): Float = {
        val f = super.getMaximumSpan(axis)
        (f * _zoomFactor).toFloat
      }

      override def getPreferredSpan(axis: Int): Float = {
        val f = super.getPreferredSpan(axis)
        (f * _zoomFactor).toFloat
      }

      override def modelToView(pos: Int, a: Shape, b: Position.Bias): Shape = {
        val scale   = _zoomFactor
        val ab      = a.getBounds
        val s       = super.modelToView(pos, ab, b)
        val sb      = s.getBounds
//        val at = AffineTransform.getScaleInstance(scale, scale)
//        at.createTransformedShape(sb)
        sb.x        = (sb.x      * scale).toInt
        sb.y        = (sb.y      * scale).toInt
        sb.width    = (sb.width  * scale).toInt
        sb.height   = (sb.height * scale).toInt
        sb
      }

      override def viewToModel(x: Float, y: Float, a: Shape, bias: Array[Position.Bias]): Int = {
        val scaleR  = 1.0 / _zoomFactor
        val ab      = a.getBounds
        val xs      = (x * scaleR).toFloat
        val ys      = (y * scaleR).toFloat
//        val at = AffineTransform.getScaleInstance(scaleR, scaleR)
//        val abS = at.createTransformedShape(ab)
//        super.viewToModel(xs, ys, abS, bias)
        ab.x        = (ab.x      * scaleR).toInt
        ab.y        = (ab.y      * scaleR).toInt
        ab.width    = (ab.width  * scaleR).toInt
        ab.height   = (ab.height * scaleR).toInt
        super.viewToModel(xs, ys, ab, bias)
      }
    }

//    class ParagraphViewWithZoom(elem: Element) extends html.ParagraphView(elem) {
//      strategy = FlowStrategyWithZoom
//
//      override def getResizeWeight(axis: Int): Int = 0
//
//      private object FlowStrategyWithZoom extends FlowView.FlowStrategy {
//        override protected def createView(fv: FlowView, startOffset: Int, spanLeft: Int, rowIndex: Int): View = {
//          val res = super.createView(fv, startOffset, spanLeft, rowIndex)
//          res
////          val MaxViewSize = 100
////          if (res.getEndOffset - res.getStartOffset <= MaxViewSize) res
////          else res.createFragment(startOffset, startOffset + MaxViewSize)
//        }
//      }
//    }
  }
}
