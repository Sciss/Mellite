/*
 *  UniverseView.scala
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

import de.sciss.lucre.{Cursor, Txn}
import de.sciss.lucre.swing.View
import de.sciss.synth.proc.Universe

trait UniverseView[T <: Txn[T]] extends View.Cursor[T] {
  implicit val universe: Universe[T]

  implicit def cursor: Cursor[T] = universe.cursor
}
