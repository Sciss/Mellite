/*
 *  ActionPreferences.scala
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

package de.sciss
package mellite.gui

import de.sciss.desktop.KeyStrokes

import scala.swing.Action
import scala.swing.event.Key

object ActionPreferences extends Action("Preferences...") {
  import KeyStrokes._

  accelerator = Some(menu1 + Key.Comma)

  def apply(): Unit = new PreferencesFrame
}