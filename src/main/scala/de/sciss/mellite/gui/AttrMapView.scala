/*
 *  AttrMapView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.gui

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.document.{AttrMapViewImpl => Impl}
import de.sciss.synth.proc.Universe

object AttrMapView {
  def apply[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx, universe: Universe[S],
                                      undoManager: UndoManager): AttrMapView[S] =
    Impl(obj)

  type Selection[S <: Sys[S]] = MapView.Selection[S]

  type Update[S <: Sys[S]] = MapView.Update[S, AttrMapView[S]]
  type SelectionChanged[S <: Sys[S]]  = MapView.SelectionChanged[S, AttrMapView[S]]
  val  SelectionChanged: MapView.SelectionChanged.type = MapView.SelectionChanged
}
trait AttrMapView[S <: stm.Sys[S]] extends MapView[S, AttrMapView[S]] {
  def obj(implicit tx: S#Tx): Obj[S]
}