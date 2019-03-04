/*
 *  WebBrowser.scala
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

package de.sciss.mellite.gui

import java.awt.GraphicsEnvironment

import de.sciss.desktop.{Window, WindowHandler}
import de.sciss.desktop.impl.WindowImpl
import de.sciss.lucre
import de.sciss.mellite.Mellite
import dotterweide.ide.{AbstractDocBrowser, DocBrowser}

import scala.swing.Component

object WebBrowser {
  def instance: DocBrowser = Impl

  private object Impl extends AbstractDocBrowser {
    private[this] val frame: WindowImpl = new WindowImpl { w =>
      def handler: WindowHandler = Mellite.windowHandler

      override protected def style: Window.Style = Window.Auxiliary

      title     = baseTitle
      contents  = Component.wrap(fxPanel)
      bounds    = {
        val gc    = GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice.getDefaultConfiguration
        val r     = gc.getBounds
        val x2    = r.x + r.width
        r.width   = math.min(r.width/2, 960)
        r.x       = x2 - r.width
        val h     = r.height
        r.height  = math.min(r.height, 960)
        r.y       = r.y + (h - r.height)/2
        r
      }
      front()
    }

    def title: String = frame.title
    def title_=(value: String): Unit = {
      lucre.swing.requireEDT()
      frame.title = value
      if (!frame.visible) frame.visible = true
    }

    def dispose(): Unit = frame.dispose()
  }
}
