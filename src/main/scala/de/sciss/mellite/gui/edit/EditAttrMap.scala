/*
 *  EditAttrMap.scala
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

package de.sciss.mellite.gui.edit

import de.sciss.lucre.expr.{Expr, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Sys}
import javax.swing.undo.{AbstractUndoableEdit, UndoableEdit}

import scala.language.higherKinds
import scala.reflect.ClassTag

object EditAttrMap {
  def add[S <: Sys[S]](name: String, obj: Obj[S], key: String, value: Obj[S])
                      (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit =
    apply(name = s"Add $name", obj = obj, key = key, value = Some(value))

  def remove[S <: Sys[S]](name: String, obj: Obj[S], key: String)
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit =
    apply(name = s"Remove $name", obj = obj, key = key, value = None)

  def apply[S <: Sys[S]](name: String, obj: Obj[S], key: String, value: Option[Obj[S]])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    val before    = obj.attr.get(key)
    val objH      = tx.newHandle(obj)
    val beforeH   = tx.newHandle(before)
    val nowH      = tx.newHandle(value)
    val res       = new ApplyImpl(name, key, objH, beforeH, nowH)
    res.perform()
    res
  }

  def expr[S <: Sys[S], A, E[~ <: Sys[~]] <: Expr[~, A]](name: String, obj: Obj[S],
                                                      key: String, value: Option[E[S]])
                          (implicit tx: S#Tx, cursor: stm.Cursor[S], tpe: Type.Expr[A, E], ct: ClassTag[E[S]]): UndoableEdit = {
    // what we do in `expr` is preserve an existing variable.
    // that is, if there is an existing value which is a variable,
    // we do not overwrite that value, but preserve that
    // variable's current child and overwrite that variable's child.
    val befOpt: Option[E[S]] = obj.attr.$[E](key)
    val before    = befOpt match {
      case Some(tpe.Var(vr)) => Some(vr())
      case other => other
    }
    import tpe.serializer
    val objH      = tx.newHandle(obj)
    val beforeH   = tx.newHandle(before)
    val nowH      = tx.newHandle(value)
    val res       = new ExprImpl[S, A, E](name, key, objH, beforeH, nowH)
    res.perform()
    res
  }

  private final class ApplyImpl[S <: Sys[S]](val name: String, val key: String,
                                             val objH   : stm.Source[S#Tx, Obj[S]],
                                             val beforeH: stm.Source[S#Tx, Option[Obj[S]]],
                                             val nowH   : stm.Source[S#Tx, Option[Obj[S]]])
                                            (implicit val cursor: stm.Cursor[S])
    extends Impl[S, Obj[S]] {

    protected def put(map: Obj.AttrMap[S], elem: Obj[S])(implicit tx: S#Tx): Unit =
      map.put(key, elem)
  }

  private final class ExprImpl[S <: Sys[S], B, E[~ <: Sys[~]] <: Expr[~, B]](
                                               val name: String, val key: String,
                                               val objH   : stm.Source[S#Tx, Obj[S]],
                                               val beforeH: stm.Source[S#Tx, Option[E[S]]],
                                               val nowH   : stm.Source[S#Tx, Option[E[S]]])
                                              (implicit val cursor: stm.Cursor[S], tpe: Type.Expr[B, E], ct: ClassTag[E[S]])
    extends Impl[S, E[S]] {

    protected def put(map: Obj.AttrMap[S], elem: E[S])(implicit tx: S#Tx): Unit = {
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

  private abstract class Impl[S <: Sys[S], A] extends AbstractUndoableEdit {
    protected def name   : String
    protected def key    : String
    protected def objH   : stm.Source[S#Tx, Obj[S]]
    protected def beforeH: stm.Source[S#Tx, Option[A]]
    protected def nowH   : stm.Source[S#Tx, Option[A]]

    protected def cursor: stm.Cursor[S]

    override def undo(): Unit = {
      super.undo()
      cursor.step { implicit tx => perform(beforeH) }
    }

    override def redo(): Unit = {
      super.redo()
      cursor.step { implicit tx => perform() }
    }

    protected def put(map: Obj.AttrMap[S], elem: A)(implicit tx: S#Tx): Unit

    private def perform(valueH: stm.Source[S#Tx, Option[A]])(implicit tx: S#Tx): Unit = {
      val map = objH().attr
      valueH().fold[Unit] {
        map.remove(key)
      } { obj =>
        put(map, obj)
      }
    }

    def perform()(implicit tx: S#Tx): Unit = perform(nowH)

    override def getPresentationName: String = name
  }
}
