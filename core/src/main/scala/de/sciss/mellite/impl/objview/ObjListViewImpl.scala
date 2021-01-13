/*
 *  ObjListViewImpl.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{BooleanObj, Cursor, Expr, Obj, Txn => LTxn}
import de.sciss.mellite.{ObjListView, ObjView}

import javax.swing.undo.UndoableEdit
import scala.collection.immutable.{IndexedSeq => Vec}
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

  def apply[T <: Txn[T]](obj: Obj[T])(implicit tx: T): ObjListView[T] = {
    val tid = obj.tpe.typeId
    // getOrElse(sys.error(s"No view for type $tid"))
    map.get(tid).fold[ObjListView[T]](GenericObjView.mkListView(obj)) { f =>
      f.mkListView(obj.asInstanceOf[f.E[T]])
    }
  }

  private var map = scala.Predef.Map.empty[Int, ObjListView.Factory]

  /** A trait that when mixed in provides `isEditable` and `tryEdit` as non-op methods. */
  trait NonEditable[T <: LTxn[T]] extends ObjListView[T] {
    override def isListCellEditable: Boolean = false

    override def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = None
  }

  trait EmptyRenderer[T <: LTxn[T]] {
    def configureListCellRenderer(label: Label): Component = label
    // def isUpdateVisible(update: Any)(implicit tx: T): Boolean = false
    def value: Any = ()
  }

  trait StringRenderer {
    def value: Any

    def configureListCellRenderer(label: Label): Component = {
      label.text = value.toString.replace('\n', ' ')
      label
    }
  }

  trait ExprLike[T <: LTxn[T], A, Ex[~ <: LTxn[~]] <: Expr[~, A]]
    extends ObjViewImpl.ExprLike[T, A, Ex] with ObjListView[T] {

    // /** Tests a value from a `Change` update. */
    // protected def testValue       (v: Any): Option[A]
    protected def convertEditValue(v: Any): Option[A]

    def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
      val tpe = exprType  // make IntelliJ happy
      convertEditValue(value).flatMap { newValue =>
        expr match {
          case tpe.Var(vr) =>
            import de.sciss.equal.Implicits._
            vr() match {
              case Expr.Const(x) if x === newValue => None
              case _ =>
                val ed = EditVar.Expr[T, A, Ex](s"Change $humanName Value", vr,
                  tpe.newConst[T](newValue))  // IntelliJ highlight bug
                Some(ed)
            }

          case _ => None
        }
      }
    }
  }

  trait SimpleExpr[T <: Txn[T], A, Ex[~ <: LTxn[~]] <: Expr[~, A]] extends ExprLike[T, A, Ex]
    with ObjListView[T] with ObjViewImpl.SimpleExpr[T, A, Ex]

  trait VectorExpr[T <: Txn[T], A, Ex[~ <: LTxn[~]] <: Expr[~, Vec[A]]] extends ExprLike[T, Vec[A], Ex]
    with ObjListView[T] with ObjViewImpl.ExprLike[T, Vec[A], Ex] with ObjViewImpl.Impl[T] {

    def value: String

    def init(ex: Ex[T])(implicit tx: T): this.type = {
      initAttrs(ex)
      addDisposable(ex.changed.react { implicit tx =>upd =>
        deferTx {
          exprValue = upd.now
        }
        fire(ObjView.Repaint(this))
      })
      this
    }

    def configureListCellRenderer(label: Label): Component = {
      label.text = value // value.iterator.map(_.toFloat).mkString(",")  // avoid excessive number of digits!
      label
    }
  }

  private final val ggCheckBox = new CheckBox()

  trait BooleanExprLike[T <: Txn[T]] extends ExprLike[T, Boolean, BooleanObj] {
    self: ObjListView[T] =>

    def exprType: Expr.Type[Boolean, BooleanObj] = BooleanObj

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
