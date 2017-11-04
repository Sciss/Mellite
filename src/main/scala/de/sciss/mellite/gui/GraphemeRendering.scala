/*
 *  BasicRendering.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import java.awt.geom.{Ellipse2D, Path2D}

/** Paint support for grapheme obj views. */
trait GraphemeRendering extends BasicRendering {
  def ellipse1: Ellipse2D
}