/*
 *  WindowPlacement.scala
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
package gui

object WindowPlacement {
  final val default = WindowPlacement(0.5f, 0.5f)
}
final case class WindowPlacement(horizontal: Float, vertical: Float, padding: Int = 20)