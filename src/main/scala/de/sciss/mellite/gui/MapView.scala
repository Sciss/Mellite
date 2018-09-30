/*
 *  MapView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.model.Model
import de.sciss.synth.proc.gui.UniverseView

object MapView {
  type Selection[S <: stm.Sys[S]] = List[(String, ObjView[S])]

  sealed trait Update[S <: stm.Sys[S], Repr] { def view: Repr }
  final case class SelectionChanged[S <: stm.Sys[S], Repr](view: Repr, selection: Selection[S])
    extends Update[S, Repr]
}
trait MapView[S <: stm.Sys[S], Repr]
  extends UniverseView[S] with View.Editable[S] with Model[MapView.Update[S, Repr]] {

  def selection: MapView.Selection[S]

  def queryKey(initial: String = "key"): Option[String]
}