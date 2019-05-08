/*
 *  Obj.scala
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

// XXX TODO --- this should go into SP
object Obj {
  implicit class ExOps(private val obj: Ex[Obj]) extends AnyVal {
    def attr[A: Attr.Bridge](key: String): Attr[A] = ???

    def attr[A: Attr.Bridge](key: String, default: Ex[A]): Attr.WithDefault[A] = ???
  }

  trait Selector[A]
}
trait Obj
