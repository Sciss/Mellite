/*
 *  NoMenuBarActions.scala
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

import de.sciss.mellite.Mellite
import de.sciss.mellite.MenuBar
import javax.swing.{JComponent, KeyStroke}

import scala.swing.{Action, Component}

/** Mixin for standard actions when no menu bar is present. */
trait NoMenuBarActions {

  protected def handleClose(): Unit

  protected def undoRedoActions: Option[(Action, Action)]

  // XXX TODO --- there should be a general mechanism for
  // binding actions even if the menu bar is absent.

  protected final def initNoMenuBarActions(c: Component): Unit = {
    val am = c.peer.getActionMap
    val im = c.peer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)

    def bind(name: String, key: KeyStroke, action: Action): Unit = {
      am.put(name, action.peer)
      im.put(key, name)
    }

    bind("file.close"     , MenuBar.keyClose    , Action(null)(handleClose        ()))
    bind("view.show-log"  , MenuBar.keyShowLog  , Action(null)(Mellite.logToFront ()))
    bind("view.clear-log" , MenuBar.keyClearLog , Action(null)(Mellite.clearLog   ()))
    undoRedoActions.foreach { case (undo, redo) =>
      bind("edit.undo", MenuBar.keyUndo, undo)
      bind("edit.redo", MenuBar.keyRedo, redo)
    }
  }
}
