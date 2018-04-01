/*
 *  TimelineRenderingImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui.impl.timeline

import de.sciss.mellite.gui.{TimelineRendering, TrackTool}
import de.sciss.mellite.gui.impl.RenderingImpl

import scala.swing.Component

//object TimelineRenderingImpl {
//}
final class TimelineRenderingImpl(component: Component, isDark: Boolean)
  extends RenderingImpl(component, isDark) with TimelineRendering {

//  import timeline.{TimelineRenderingImpl => Impl}

  var ttMoveState     : TrackTool.Move      = TrackTool.NoMove
  var ttResizeState   : TrackTool.Resize    = TrackTool.NoResize
  var ttGainState     : TrackTool.Gain      = TrackTool.NoGain
  var ttFadeState     : TrackTool.Fade      = TrackTool.NoFade
  var ttFunctionState : TrackTool.Function  = TrackTool.NoFunction
}