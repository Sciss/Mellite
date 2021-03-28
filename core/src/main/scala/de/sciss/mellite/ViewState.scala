/*
 *  ViewState.scala
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

import de.sciss.lucre.{Expr, Obj, Txn}

object ViewState {
  def apply[A, Repr[~ <: Txn[~]] <: Expr[~, A]](key: String, tpe: Expr.Type[A, Repr], value: A): ViewState =
    Impl(key, tpe, value)

  private case class Impl[A, Repr[~ <: Txn[~]] <: Expr[~, A]](key: String, tpe: Expr.Type[A, Repr], value: A)
    extends ViewState {

    def set[T <: Txn[T]](attr: Obj.AttrMap[T])(implicit tx: T): Unit = {
      val valueObj = tpe.newConst[T](value)
      attr.get(key) match {
        case Some(obj) if obj.tpe == tpe =>
          val objC = obj.asInstanceOf[Repr[T]]
          objC match {
            case tpe.Var(vr) => vr() = valueObj
            case _ => attr.put(key, valueObj)
          }

        case _ => attr.put(key, valueObj)
      }
    }
  }
}
trait ViewState {
  def set[T <: Txn[T]](attr: Obj.AttrMap[T])(implicit tx: T): Unit
}