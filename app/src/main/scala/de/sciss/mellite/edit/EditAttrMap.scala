/*
 *  EditAttrMap.scala
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

package de.sciss.mellite.edit

import de.sciss.lucre.expr.{Expr, Type}
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.{Obj, Sys}
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

import scala.reflect.ClassTag

object EditAttrMap {
  def add[T <: Txn[T]](name: String, obj: Obj[T], key: String, value: Obj[T])
                      (implicit tx: T, cursor: Cursor[T]): UndoableEdit =
    apply(name = s"Add $name", obj = obj, key = key, value = Some(value))

  def remove[T <: Txn[T]](name: String, obj: Obj[T], key: String)
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit =
    apply(name = s"Remove $name", obj = obj, key = key, value = None)

  def apply[T <: Txn[T]](name: String, obj: Obj[T], key: String, value: Option[Obj[T]])
                        (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    val before    = obj.attr.get(key)
    val objH      = tx.newHandle(obj)
    val beforeH   = tx.newHandle(before)
    val nowH      = tx.newHandle(value)
    val res       = new ApplyImpl(name, key, objH, beforeH, nowH)
    res.perform()
    res
  }

  def expr[T <: Txn[T], A, E[~ <: Sys[~]] <: Expr[~, A]](name: String, obj: Obj[T],
                                                      key: String, value: Option[E[T]])
                          (implicit tx: T, cursor: Cursor[T], tpe: Expr.Type[A, E], ct: ClassTag[E[T]]): UndoableEdit = {
    // what we do in `expr` is preserve an existing variable.
    // that is, if there is an existing value which is a variable,
    // we do not overwrite that value, but preserve that
    // variable's current child and overwrite that variable's child.
    val befOpt: Option[E[T]] = obj.attr.$[E](key)
    val before    = befOpt match {
      case Some(tpe.Var(vr)) => Some(vr())
      case other => other
    }
    import tpe.serializer
    val objH      = tx.newHandle(obj)
    val beforeH   = tx.newHandle(before)
    val nowH      = tx.newHandle(value)
    val res       = new ExprImpl[T, A, E](name, key, objH, beforeH, nowH)
    res.perform()
    res
  }

  private final class ApplyImpl[T <: Txn[T]](val name: String, val key: String,
                                             val objH   : Source[T, Obj[T]],
                                             val beforeH: Source[T, Option[Obj[T]]],
                                             val nowH   : Source[T, Option[Obj[T]]])
                                            (implicit val cursor: Cursor[T])
    extends Impl[T, Obj[T]] {

    protected def put(map: Obj.AttrMap[T], elem: Obj[T])(implicit tx: T): Unit =
      map.put(key, elem)
  }

  private final class ExprImpl[T <: Txn[T], B, E[~ <: Sys[~]] <: Expr[~, B]](
                                               val name: String, val key: String,
                                               val objH   : Source[T, Obj[T]],
                                               val beforeH: Source[T, Option[E[T]]],
                                               val nowH   : Source[T, Option[E[T]]])
                                              (implicit val cursor: Cursor[T], tpe: Expr.Type[B, E], ct: ClassTag[E[T]])
    extends Impl[T, E[T]] {

    protected def put(map: Obj.AttrMap[T], elem: E[T])(implicit tx: T): Unit = {
      val opt = map.$[E](key)
      opt match {
        case Some(tpe.Var(vr)) =>
          // see above for an explanation about how we preserve a variable
          import de.sciss.equal.Implicits._
          if (vr === elem) throw new IllegalArgumentException(s"Cyclic reference setting variable $vr")
          vr() = elem
        case _ => map.put(key, elem) // Obj(mkElem(elem)))
      }
    }
  }

  private abstract class Impl[T <: Txn[T], A] extends AbstractUndoableEdit {
    protected def name   : String
    protected def key    : String
    protected def objH   : Source[T, Obj[T]]
    protected def beforeH: Source[T, Option[A]]
    protected def nowH   : Source[T, Option[A]]

    protected def cursor: Cursor[T]

    override def undo(): Unit = {
      super.undo()
      cursor.step { implicit tx => perform(beforeH) }
    }

    override def redo(): Unit = {
      super.redo()
      cursor.step { implicit tx => perform() }
    }

    protected def put(map: Obj.AttrMap[T], elem: A)(implicit tx: T): Unit

    private def perform(valueH: Source[T, Option[A]])(implicit tx: T): Unit = {
      val map = objH().attr
      valueH().fold[Unit] {
        map.remove(key)
      } { obj =>
        put(map, obj)
      }
    }

    def perform()(implicit tx: T): Unit = perform(nowH)

    override def getPresentationName: String = name
  }
}
