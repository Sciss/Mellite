/*
 *  GraphemeRenderingImpl.scala
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

package de.sciss.mellite.impl.grapheme

import java.awt.geom.{AffineTransform, Area, Ellipse2D}
import de.sciss.mellite.{GraphemeRendering, GraphemeTool}
import de.sciss.mellite.impl.RenderingImpl

import java.awt.image.ImageObserver
import scala.swing.Component

final class GraphemeRenderingImpl(component: Component, isDark: Boolean)
  extends RenderingImpl(isDark) with GraphemeRendering {

  def imageObserver: ImageObserver = component.peer

  val ellipse1  : Ellipse2D       = new Ellipse2D.Float()
  val transform1: AffineTransform = new AffineTransform()
  val area1     : Area            = new Area
  val area2     : Area            = new Area

  var ttMoveState: GraphemeTool.Move  = GraphemeTool.NoMove
}
