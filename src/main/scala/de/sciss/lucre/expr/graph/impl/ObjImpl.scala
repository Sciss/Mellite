/*
 *  ObjImpl.scala
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

package de.sciss.lucre.expr.graph.impl

import de.sciss.lucre.expr.graph.Obj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys

final class ObjImpl[In <: Sys[In]](in: stm.Source[In#Tx, stm.Obj[In]], system: In) extends Obj {
  private[graph] def peer[S <: Sys[S]](implicit tx: S#Tx): stm.Obj[S] = {
    require (tx.system == system)
    val out = in.asInstanceOf[stm.Source[S#Tx, stm.Obj[S]]]
    out()
  }
}
