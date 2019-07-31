/*
 *  UniverseView.scala
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

package de.sciss.mellite

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.synth.proc.Universe

trait UniverseView[S <: Sys[S]] extends View.Cursor[S] {
  implicit val universe: Universe[S]

  implicit def cursor: stm.Cursor[S] = universe.cursor
}
