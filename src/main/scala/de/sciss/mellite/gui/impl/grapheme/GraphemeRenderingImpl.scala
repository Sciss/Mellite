/*
 *  GraphemeRenderingImpl.scala
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
package gui.impl.grapheme

import de.sciss.mellite.gui.GraphemeRendering
import de.sciss.mellite.gui.impl.RenderingImpl

import scala.swing.Component

final class GraphemeRenderingImpl(component: Component, isDark: Boolean)
  extends RenderingImpl(component, isDark) with GraphemeRendering
