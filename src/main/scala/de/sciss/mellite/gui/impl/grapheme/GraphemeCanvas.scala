/*
 *  GraphemeCanvas.scala
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
package impl
package grapheme

import de.sciss.audiowidgets.impl.TimelineCanvasImpl
import de.sciss.lucre.stm.Sys

trait GraphemeCanvas[S <: Sys[S]] extends TimelineCanvasImpl {
  // def timeline(implicit tx: S#Tx): Timeline[S]

  def selectionModel: GraphemeObjView.SelectionModel[S]

  // def intersect(span: Span): Iterator[TimelineObjView[S]]

  def findView(frame: Long): Option[GraphemeObjView[S]]

  // def findViews(r: TrackTool.Rectangular): Iterator[GraphemeObjView[S]]

  // ---- impl ----

  private[this] val selectionListener: SelectionModel.Listener[S, GraphemeObjView[S]] = {
    case SelectionModel.Update(added, removed) =>
      canvasComponent.repaint() // XXX TODO: dirty rectangle optimization
  }

  override protected def componentShown(): Unit = {
    super.componentShown()
    selectionModel.addListener(selectionListener)
  }

  override protected def componentHidden(): Unit = {
    super.componentHidden()
    selectionModel.removeListener(selectionListener)
  }
}