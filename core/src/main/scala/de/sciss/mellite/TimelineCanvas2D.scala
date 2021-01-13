/*
 *  TimelineCanvas2D.scala
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

import de.sciss.audiowidgets.TimelineCanvas
import de.sciss.lucre.Txn
import de.sciss.span.Span

trait TimelineCanvas2D[T <: Txn[T], Y, Child] extends TimelineCanvas {
//  def timeline(implicit tx: T): Timeline[T]

  def selectionModel: SelectionModel[T, Child]

  def iterator: Iterator[Child]

  def intersect(span: Span.NonVoid): Iterator[Child]

  def findChildView(frame: Long, modelY: Y): Option[Child]

  def findChildViews(r: BasicTool.Rectangular[Y]): Iterator[Child]

  def screenToModelPos(y: Int): Y

  def screenToModelPosF(y: Int): Double = throw new NotImplementedError()

  def screenToModelExtent(dy: Int): Y

  def screenToModelExtentF(dy: Int): Double = throw new NotImplementedError()

  def modelPosToScreen(modelY: Y): Double

  def modelExtentToScreen(modelY: Y): Double

//  def modelYNumeric: Numeric[Y]

  def modelYBox(a: Y, b: Y): (Y, Y)

//  def timelineTools: TimelineTools[T]
}
