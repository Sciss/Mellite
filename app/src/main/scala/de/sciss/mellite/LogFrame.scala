/*
 *  LogFrame.scala
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

package de.sciss.mellite

import java.awt.Font

import de.sciss.desktop
import de.sciss.desktop.impl.LogWindowImpl
import de.sciss.desktop.{LogPane, Window, WindowHandler}
import de.sciss.mellite.impl.component.{NoMenuBarActions, ZoomSupport}

import scala.swing.Action

object LogFrame {
  val horizontalPlacement   = 1.0f
  val verticalPlacement     = 1.0f
  val placementPadding      = 20

  lazy val instance: LogFrame  = new LogWindowImpl with LogFrame with ZoomSupport with NoMenuBarActions { frame =>
    def handler: WindowHandler = Application.windowHandler

//    log.background  = Style.BlueForest.background
//    log.foreground  = Style.BlueForest.foreground
    log.font        = new Font(Font.MONOSPACED, Font.PLAIN, 12)

    initZoom            (log.component)
    initNoMenuBarActions(log.component)

    pack()  // after changing font!
    desktop.Util.placeWindow(frame, horizontal = horizontalPlacement, vertical = verticalPlacement, padding = placementPadding)

    protected def handleClose(): Unit = hide()

    protected def undoRedoActions: Option[(Action, Action)] = None

    protected def setZoomFactor(f: Float): Unit =
      log.font = log.font.deriveFont(12 * f)
  }
}
trait LogFrame extends Window {
  def log: LogPane
}