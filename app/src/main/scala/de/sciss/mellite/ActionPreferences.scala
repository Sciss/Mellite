/*
 *  ActionPreferences.scala
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

package de.sciss.mellite

import de.sciss.desktop.KeyStrokes

import scala.swing.Action
import scala.swing.event.Key

object ActionPreferences extends Action("Preferences...") {
  import KeyStrokes._

  accelerator = Some(menu1 + Key.Comma)

  def apply(): Unit = open()

  val Tab: PreferencesFrame.Tab.type = PreferencesFrame.Tab

  def open(tab: Tab.Value = Tab.Default): Unit =
    new PreferencesFrame(tab)
}