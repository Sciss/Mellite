/*
 *  AttrCellViewImpl.scala
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

import de.sciss.lucre.expr.{Expr, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.lucre.swing.CellView
import de.sciss.lucre.swing.impl.CellViewImpl
import de.sciss.model.Change
import de.sciss.serial.Serializer

import scala.concurrent.stm.Ref
import scala.language.higherKinds
import scala.reflect.ClassTag

object AttrCellViewImpl {
  private[gui] trait Basic[S <: Sys[S], A, E[~ <: Sys[~]] <: Expr[~, A]]
    extends CellViewImpl.Basic[S#Tx, Option[A]] {

    protected def h: stm.Source[S#Tx, Obj.AttrMap[S]]
    protected val key: String

    // implicit protected def companion: Elem.Companion[E]
    implicit protected val tpe: Type.Expr[A, E]

    implicit protected def classTag: ClassTag[E[S]]

    type Repr = Option[E[S]] // Expr[S, A]]

    def react(fun: S#Tx => Option[A] => Unit)(implicit tx: S#Tx): Disposable[S#Tx] =
      new ExprMapLikeObs(map = h(), key = key, fun = fun, tx0 = tx)

    def repr(implicit tx: S#Tx): Repr = {
      val opt = h().$[E](key)
      opt.map {
        case tpe.Var(vr) => vr()
        case other => other
      }
    }

    def apply()(implicit tx: S#Tx): Option[A] = repr.map(_.value)
  }

  // XXX TODO --- lot's of overlap with CellViewImpl
  private[this] final class ExprMapLikeObs[S <: Sys[S], A, E[~ <: Sys[~]] <: Expr[~, A]](
      map: Obj.AttrMap[S], key: String, fun: S#Tx => Option[A] => Unit, tx0: S#Tx)(implicit tpe: Type.Expr[A, E])
    extends Disposable[S#Tx] {

    private[this] val valObs = Ref(null: Disposable[S#Tx])

    private[this] def obsAdded(value: Obj[S])(implicit tx: S#Tx): Unit = {
      val valueT = value.asInstanceOf[E[S]]
      valueAdded(valueT)
      // XXX TODO -- if we moved this into `valueAdded`, the contract
      // could be that initially the view is updated
      val now0 = valueT.value
      fun(tx)(Some(now0))
    }

    @inline
    private[this] def obsRemoved()(implicit tx: S#Tx): Unit =
      if (valueRemoved()) fun(tx)(None)

    private[this] val mapObs = map.changed.react { implicit tx => u =>
      u.changes.foreach {
        case Obj.AttrAdded   (`key`, value) if value.tpe == tpe => obsAdded  (value)
        case Obj.AttrRemoved (`key`, value) if value.tpe == tpe => obsRemoved()
        case Obj.AttrReplaced(`key`, before, now) =>
          if      (now   .tpe == tpe) obsAdded(now)
          else if (before.tpe == tpe) obsRemoved()
        case _ =>
      }
    } (tx0)

    map.get(key)(tx0).foreach { value =>
      if (value.tpe == tpe) valueAdded(value.asInstanceOf[E[S]])(tx0)
    }

    private[this] def valueAdded(value: E[S])(implicit tx: S#Tx): Unit = {
      val res = value.changed.react { implicit tx => {
        case Change(_, now) =>
          fun(tx)(Some(now))
        //            val opt = mapUpdate(ch)
        //            if (opt.isDefined) fun(tx)(opt)
        case _ =>  // XXX TODO -- should we ask for expr.value ?
      }}
      val v = valObs.swap(res)(tx.peer)
      if (v != null) v.dispose()
    }

    private[this] def valueRemoved()(implicit tx: S#Tx): Boolean = {
      val v   = valObs.swap(null)(tx.peer)
      val res = v != null
      if (res) v.dispose()
      res
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      valueRemoved()
      mapObs.dispose()
    }
  }

  //  // actually unused, because obj.attr is always modifiable
  //  private[gui] final class Impl[S <: Sys[S], A, E[~ <: Sys[~]] <: Elem[~] { type Peer = Expr[~, A] }](
  //         protected val h: stm.Source[S#Tx, Obj[S]],
  //         protected val key: String)(implicit protected val companion: Elem.Companion[E])
  //    extends Basic[S, A, E]

  private[gui] final class ModImpl[S <: Sys[S], A, E[~ <: Sys[~]] <: Expr[~, A]](
         protected val h: stm.Source[S#Tx, Obj.AttrMap[S]],
         protected val key: String)(implicit val tpe: Type.Expr[A, E], protected val classTag: ClassTag[E[S]])
    extends Basic[S, A, E] with CellView.Var[S, Option[A]] {

    def serializer: Serializer[S#Tx, S#Acc, Repr] = {
      implicit val exSer: Serializer[S#Tx, S#Acc, E[S]] = tpe.serializer[S]
      Serializer.option[S#Tx, S#Acc, E[S]]
    }

    protected def mapUpdate(ch: Change[A]): Option[A] = if (ch.isSignificant) Some(ch.now) else None

    def repr_=(value: Repr)(implicit tx: S#Tx): Unit = value.fold[Unit] {
      h().remove(key)
    } { ex =>
      val map = h()
      map.$[E](key) match {
        case Some(tpe.Var(vr)) => vr() = ex
        case _ =>
          val aObj = tpe.Var.unapply[S](ex).getOrElse(tpe.newVar(ex))
          map.put(key, aObj)
      }
    }

    def lift(value: Option[A])(implicit tx: S#Tx): Repr = value.map(tpe.newConst[S](_))

    def update(v: Option[A])(implicit tx: S#Tx): Unit = repr_=(lift(v))
  }
}