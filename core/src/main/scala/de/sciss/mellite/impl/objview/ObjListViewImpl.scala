/*
 *  ObjListViewImpl.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.lucre.expr.{BooleanObj, Expr, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ObjListView
import javax.swing.undo.UndoableEdit

import scala.language.higherKinds
import scala.swing.{CheckBox, Component, Label}
import scala.util.Try

object ObjListViewImpl {
  private val sync = new AnyRef

  def addFactory(f: ObjListView.Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeId
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[ObjListView.Factory] = map.values

  def apply[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx): ObjListView[S] = {
    val tid = obj.tpe.typeId
    // getOrElse(sys.error(s"No view for type $tid"))
    map.get(tid).fold[ObjListView[S]](GenericObjView.mkListView(obj)) { f =>
      f.mkListView(obj.asInstanceOf[f.E[S]])
    }
  }

  private var map = scala.Predef.Map.empty[Int, ObjListView.Factory]

  /** A trait that when mixed in provides `isEditable` and `tryEdit` as non-op methods. */
  trait NonEditable[S <: stm.Sys[S]] extends ObjListView[S] {
    override def isListCellEditable: Boolean = false

    override def tryEditListCell(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = None
  }

  trait EmptyRenderer[S <: stm.Sys[S]] {
    def configureListCellRenderer(label: Label): Component = label
    // def isUpdateVisible(update: Any)(implicit tx: S#Tx): Boolean = false
    def value: Any = ()
  }

  trait StringRenderer {
    def value: Any

    def configureListCellRenderer(label: Label): Component = {
      label.text = value.toString.replace('\n', ' ')
      label
    }
  }

  trait ExprLike[S <: stm.Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]]
    extends ObjViewImpl.ExprLike[S, A, Ex] with ObjListView[S] {

    // /** Tests a value from a `Change` update. */
    // protected def testValue       (v: Any): Option[A]
    protected def convertEditValue(v: Any): Option[A]

    def tryEditListCell(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
      val tpe = exprType  // make IntelliJ happy
      convertEditValue(value).flatMap { newValue =>
        expr match {
          case tpe.Var(vr) =>
            import de.sciss.equal.Implicits._
            vr() match {
              case Expr.Const(x) if x === newValue => None
              case _ =>
                val ed = EditVar.Expr[S, A, Ex](s"Change $humanName Value", vr,
                  tpe.newConst[S](newValue))  // IntelliJ highlight bug
                Some(ed)
            }

          case _ => None
        }
      }
    }
  }

  trait SimpleExpr[S <: Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ExprLike[S, A, Ex]
    with ObjListView[S] with ObjViewImpl.SimpleExpr[S, A, Ex]

  private final val ggCheckBox = new CheckBox()

  trait BooleanExprLike[S <: Sys[S]] extends ExprLike[S, Boolean, BooleanObj] {
    _: ObjListView[S] =>

    def exprType: Type.Expr[Boolean, BooleanObj] = BooleanObj

    def convertEditValue(v: Any): Option[Boolean] = v match {
      case num: Boolean  => Some(num)
      case s: String     => Try(s.toBoolean).toOption
    }

    def testValue(v: Any): Option[Boolean] = v match {
      case i: Boolean  => Some(i)
      case _            => None
    }

    def configureListCellRenderer(label: Label): Component = {
      ggCheckBox.selected   = exprValue
      ggCheckBox.background = label.background
      ggCheckBox
    }
  }
}
