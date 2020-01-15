/*
 *  TimelineRendering.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import java.awt.{Paint, Stroke}

/** Paint support for timeline obj views. */
trait TimelineRendering extends BasicRendering {
  def pntInlet                    : Paint
  def pntInletSpan                : Paint
  def strokeInletSpan             : Stroke

  def ttMoveState                 : TimelineTool.Move
  def ttResizeState               : TimelineTool.Resize
  def ttGainState                 : TimelineTool.Gain
  def ttFadeState                 : TimelineTool.Fade
  def ttFunctionState             : TimelineTool.Add
}