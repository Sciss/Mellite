/*
 *  ZoomSupport.scala
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

package de.sciss.mellite.gui.impl.component

import de.sciss.desktop.{KeyStrokes, Util}
import de.sciss.numbers

import scala.swing.event.{Key, SelectionChanged}
import scala.swing.{ComboBox, Component}

/** Mixin for views that allow step-wise zooming. */
trait ZoomSupport {
  protected def setZoomFactor(f: Float): Unit

  private def stdZoomItems = Vector(25, 50, 75, 100, 125, 150, 200, 250, 300, 350, 400)

  private final class Percent(value: Int) {
    override def toString: String = s"$value%"

    def fraction: Float = value * 0.01f
  }

  private[this] var _zoomFactor = 1f

  protected final def zoomFactor: Float = _zoomFactor

  protected final def initZoomWithComboBox(): ComboBox[_] = {
    val zoomItems     = stdZoomItems
    val c             = new ComboBox[Percent](zoomItems.map(new Percent(_)))
    c.selection.index = zoomItems.indexOf(100)
    c.listenTo(c.selection)
    c.reactions += {
      case SelectionChanged(_) =>
        val f = c.selection.item.fraction
        if (_zoomFactor != f) {
          _zoomFactor = f
          setZoomFactor(f)
        }
    }
    initZoomImpl(c, zoomItems, Some(c))
    c
  }

  protected final def initZoom(parent: Component): Unit = {
    initZoomImpl(parent, stdZoomItems, None)
  }

  private def initZoomImpl(parent: Component, zoomItems: Vector[Int], combo: Option[ComboBox[Percent]]): Unit = {
    val zoom100     = zoomItems.indexOf(100)
    var zoomIdx     = zoom100

    def zoom(newIdx0: Int): Unit = {
      import numbers.Implicits._
      val newIdx = newIdx0.clip(0, zoomItems.size - 1)
      if (zoomIdx != newIdx) {
        zoomIdx = newIdx
        val f   = zoomItems(newIdx) * 0.01f
        if (_zoomFactor != f) {
          _zoomFactor = f
          setZoomFactor(f)
          combo.foreach { c =>
            c.selection.index = newIdx
          }
        }
      }
    }

    def zoomIn    (): Unit = zoom(zoomIdx + 1)
    def zoomOut   (): Unit = zoom(zoomIdx - 1)
    def zoomReset (): Unit = zoom(zoom100)

    import KeyStrokes._
    Util.addGlobalAction(parent, "dec-zoom", ctrl + Key.Minus          )(zoomOut   ())
    Util.addGlobalAction(parent, "inc-zoom", ctrl + Key.Plus           )(zoomIn    ())
    Util.addGlobalAction(parent, "inc-zoom", shift + ctrl + Key.Equals )(zoomIn    ())
    Util.addGlobalAction(parent, "reset-zoom", ctrl + Key.Key0         )(zoomReset ())
  }
}
