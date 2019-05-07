/*
 *  Timed.scala
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

package de.sciss.lucre.expr.graph

import de.sciss.lucre.expr.Ex
import de.sciss.span.SpanLike

object Timed {
  implicit class ExOps[A](private val t: Ex[Timed[A]]) extends AnyVal {
    def collect[B: Obj.Selector]: Ex[Timed[B]] = ???

    def span  : Ex[SpanLike]  = ???
    def value : Ex[A]         = ???
  }

//  implicit class ExSeqOps[A](private val t: Ex[ISeq[Timed[A]]]) extends AnyVal {
//    def collect[B: Obj.Selector]: Ex[ISeq[Timed[B]]] = ???
//
//    def span  : Ex[ISeq[SpanLike]]  = ???
//    def value : Ex[ISeq[A]]         = ???
//  }
}
trait Timed[A] {
  def span: SpanLike

  def value: A
}
