/*
 *  DnD.scala
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

import java.awt.datatransfer
import java.awt.event.{MouseAdapter, MouseEvent}

import de.sciss.mellite.gui.{GUI, Shapes}
import javax.swing.TransferHandler.COPY
import javax.swing.{JComponent, TransferHandler}

abstract class DragSourceButton(actions: Int = TransferHandler.COPY) extends swing.Button { button =>
  protected def createTransferable(): Option[datatransfer.Transferable]

  private object Transfer extends TransferHandler {
    override def getSourceActions(c: JComponent): Int =
      if (enabled) actions else TransferHandler.NONE

    override def createTransferable(c: JComponent): datatransfer.Transferable = {
      if (!enabled) return null
      button.createTransferable().orNull
    }
  }

  focusable     = false
  icon          = GUI.iconNormal  (Shapes.Share)
  disabledIcon  = GUI.iconDisabled(Shapes.Share)
  peer.setTransferHandler(Transfer)

  private var dndInitX    = 0
  private var dndInitY    = 0
  private var dndPressed  = false
  private var dndStarted  = false

  private object Mouse extends MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit = {
      dndInitX	  = e.getX
      dndInitY    = e.getY
      dndPressed  = true
      dndStarted	= false
    }

    override def mouseReleased(e: MouseEvent): Unit = {
      dndPressed  = false
      dndStarted	= false
    }

    override def mouseDragged(e: MouseEvent): Unit =
      if (dndPressed && !dndStarted && ((math.abs(e.getX - dndInitX) > 5) || (math.abs(e.getY - dndInitY) > 5))) {
        Transfer.exportAsDrag(peer, e, COPY)
        dndStarted = true
      }
  }

  peer.addMouseListener      (Mouse)
  peer.addMouseMotionListener(Mouse)
}