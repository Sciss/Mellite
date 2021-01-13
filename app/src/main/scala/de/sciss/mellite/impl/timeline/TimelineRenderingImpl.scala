/*
 *  TimelineRenderingImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.mellite.{TimelineRendering, TimelineTool}
import de.sciss.mellite.impl.RenderingImpl

import java.awt.image.ImageObserver
import scala.swing.Component

//object TimelineRenderingImpl {
//}
final class TimelineRenderingImpl(component: Component, isDark: Boolean)
  extends RenderingImpl(isDark) with TimelineRendering {

  def imageObserver: ImageObserver = component.peer

  var ttMoveState     : TimelineTool.Move     = TimelineTool.NoMove
  var ttResizeState   : TimelineTool.Resize  = TimelineTool.NoResize
  var ttGainState     : TimelineTool.Gain     = TimelineTool.NoGain
  var ttFadeState     : TimelineTool.Fade     = TimelineTool.NoFade
  var ttFunctionState : TimelineTool.Add      = TimelineTool.NoFunction
}