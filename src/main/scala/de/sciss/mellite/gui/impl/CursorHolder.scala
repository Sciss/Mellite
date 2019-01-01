/*
 *  CursorHolder.scala
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

package de.sciss.mellite.gui.impl

import de.sciss.lucre.stm.{Cursor, Sys}

trait CursorHolder[S <: Sys[S]] {
  protected def cursor: Cursor[S]

  final protected def atomic[A](fun: S#Tx => A): A = cursor.step(fun)
}
