/*
 *  BaselineFlowPanel.scala
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

package de.sciss.mellite.impl.component

import javax.swing.JPanel
import scala.swing.{Component, FlowPanel}

/** A horizontal baseline-aligned `FlowPanel` that reports its baseline based on the first component */
class BaselineFlowPanel(components: Component*) extends FlowPanel(components: _*) {
  override lazy val peer: JPanel =
    new JPanel(new java.awt.FlowLayout(FlowPanel.Alignment.Leading.id)) with SuperMixin {
      override def getBaseline(width: Int, height: Int): Int = {
        components.head.peer.getBaseline(width, height)
      }
    }

  alignOnBaseline = true
  vGap = 0
}
