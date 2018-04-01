/*
 *  TimelineRendering.scala
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
package gui

import java.awt.{Paint, Stroke}

/** Paint support for timeline obj views. */
trait TimelineRendering extends BasicRendering {
  def pntInlet                    : Paint
  def pntInletSpan                : Paint
  def strokeInletSpan             : Stroke

  def ttMoveState                 : TrackTool.Move
  def ttResizeState               : TrackTool.Resize
  def ttGainState                 : TrackTool.Gain
  def ttFadeState                 : TrackTool.Fade
  def ttFunctionState             : TrackTool.Function
}