/*
 *  TimeLabel.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package component

import javax.swing.JComponent
import java.awt.{Graphics, Graphics2D, Dimension}
import de.sciss.audiowidgets.LCDFont

object TimeLabel {
  private val prefSz = new Dimension(112, 16)
}

class TimeLabel extends JComponent {
  import TimeLabel._

  private var millisVar = 0L
  private var textVar   = ""

  updateText()

  def millis: Long = millisVar

  def millis_=(value: Long): Unit = {
    millisVar = value
    updateText()
    repaint()
  }

  private def updateText(): Unit = {
    val neg     = millisVar < 0
    val millis0 = if (neg) -millisVar else millisVar
    val millis  = millis0 % 1000
    val secs0   = millis0 / 1000
    val secs    = secs0 % 60
    val mins0   = secs0 / 60
    val mins    = mins0 % 60
    val hours   = math.min( 99, mins0 / 60 )
    val sb      = new StringBuilder( 12 )
    sb.append(if (neg) '-' else { if(hours <= 9) ' ' else ((hours / 10) + 48).toChar })
    sb.append(((hours % 10) + 48).toChar)
    sb.append(':')
    sb.append(((mins / 10) + 48).toChar)
    sb.append(((mins % 10) + 48).toChar)
    sb.append(':')
    sb.append(((secs / 10) + 48).toChar)
    sb.append(((secs % 10) + 48).toChar)
    sb.append('.')
    sb.append(((millis / 100) + 48).toChar)
    sb.append((((millis / 10) % 10) + 48).toChar)
    sb.append(((millis % 10) + 48).toChar)
    textVar = sb.toString()
  }

  override def getPreferredSize: Dimension = prefSz

  override def paintComponent(g: Graphics): Unit = {
    val g2 = g.asInstanceOf[Graphics2D]
    g2.setFont(LCDFont())
    val x = ((getWidth - 112) >> 1) + 2
    val y = ((getHeight - 16) >> 1) + 16
    g2.drawString(textVar, x, y)
  }
}