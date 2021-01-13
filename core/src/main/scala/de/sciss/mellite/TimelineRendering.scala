/*
 *  TimelineRendering.scala
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

import de.sciss.synth.Curve

import java.awt.{Graphics2D, Paint, Stroke}

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

  /** Paints a standardized rendering of a fade.
    *
    * @param g      the target graphics context
    * @param curve  the fade curve
    * @param fw     the fade width in pixels
    * @param pyi    the vertical paint position in pixels
    * @param phi    the paint height in pixels
    * @param y1     the logical start level of the fade
    * @param y2     the logical end level of the fade
    * @param x      the horizontal paint position in pixels
    * @param x0     the horizontal closing position (last line segment) in pixels.
    *               for fade-in, this would be the same as `x`, for fade-out, this
    *               would be x + fw
    */
  def paintFade(g: Graphics2D, curve: Curve, fw: Float, pyi: Int, phi: Int,
                y1: Float, y2: Float, x: Float, x0: Float): Unit
}