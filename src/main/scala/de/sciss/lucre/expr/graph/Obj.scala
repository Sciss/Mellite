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

import de.sciss.lucre.aux.Aux
import de.sciss.lucre.event.impl.IGenerator
import de.sciss.lucre.event.{IEvent, IPull, ITargets}
import de.sciss.lucre.expr.graph.{Attr => _Attr}
import de.sciss.lucre.expr.{CellView, Context, IExpr}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Disposable, Sys}
import de.sciss.model.Change

import scala.concurrent.stm.Ref

// XXX TODO --- this should go into Lucre
object Obj {
  implicit class ExOps(private val obj: Ex[Obj]) extends AnyVal {
    def attr[A: _Attr.Bridge](key: String): Obj.Attr[A] = Obj.Attr(obj, key)

    def attr[A: _Attr.Bridge](key: String, default: Ex[A]): _Attr.WithDefault[A] = ???
  }

  trait Selector[A]

  private final class AttrExpanded[S <: Sys[S], A](obj: IExpr[S, Obj], key: String, tx0: S#Tx)
                                              (implicit protected val targets: ITargets[S], bridge: _Attr.Bridge[A])
    extends IExpr[S, Option[A]] with IGenerator[S, Change[Option[A]]] {

    private[this] val viewRef   = Ref.make[CellView.Var[S, Option[A]]]
    private[this] val valueRef  = Ref.make[Option[A]]
    private[this] val obsRef    = Ref.make[Disposable[S#Tx]]
    private[this] val objObs    = obj.changed.react { implicit tx => upd =>
      setObj(upd.now, init = false)
    } (tx0)

    private def setNewValue(now: Option[A])(implicit tx: S#Tx): Unit = {
      val before = valueRef.swap(now)
      if (before != now) {
        fire(Change(before, now))
      }
    }

    private def setObj(newObj: Obj, init: Boolean)(implicit tx: S#Tx): Unit = {
      println(s"newObj = $newObj, bridge = $bridge, key = $key")
      val newView = bridge.cellView(newObj.peer, key)
      viewRef()   = newView
      val obsNew = newView.react { implicit tx => now =>
        setNewValue(now)
      }
      val now = newView()
      if (init) {
        obsRef  () = obsNew
        valueRef() = now
      } else {
        obsRef.swap(obsNew).dispose()
        setNewValue(now)
      }
    }

    // ---- init ----
    setObj(obj.value(tx0), init = true)(tx0)

    def value(implicit tx: S#Tx): Option[A] = viewRef.get.apply()

    private[lucre] def pullUpdate(pull: IPull[S])(implicit tx: S#Tx): Option[Change[Option[A]]] =
      Some(pull.resolve)

    def changed: IEvent[S, Change[Option[A]]] = this

    def dispose()(implicit tx: S#Tx): Unit = {
      objObs  .dispose()
      obsRef().dispose()
    }
  }

  // XXX TODO --- this should be merged with Lucre
  final case class Attr[A](obj: Ex[Obj], key: String)(implicit val bridge: _Attr.Bridge[A])
    extends Ex[Option[A]] with _Attr.Like[A] {

    type Repr[S <: Sys[S]] = IExpr[S, Option[A]]

    override def productPrefix: String = s"Obj$$Attr" // serialization

    protected def mkRepr[S <: Sys[S]](implicit ctx: Context[S], tx: S#Tx): Repr[S] = {
      import ctx.targets
      new AttrExpanded(obj.expand[S], key, tx)
    }

    def update(in: Ex[A]): Control = ???

    def aux: List[Aux] = bridge :: Nil
  }
}
trait Obj {
  private[graph] def peer[S <: Sys[S]](implicit tx: S#Tx): stm.Obj[S]
}
