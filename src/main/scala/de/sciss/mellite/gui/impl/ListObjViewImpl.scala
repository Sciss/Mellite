/*
 *  ListObjViewImpl.scala
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

import javax.swing.undo.UndoableEdit

import de.sciss.lucre.confluent.Access
import de.sciss.lucre.expr.{BooleanObj, Expr, Type}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.{Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.stm
import de.sciss.mellite.gui.impl.markdown.MarkdownObjView
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.serial.Serializer
import de.sciss.synth.proc.{Confluent, Workspace}

import scala.language.higherKinds
import scala.swing.{CheckBox, Component, Label}
import scala.util.Try

object ListObjViewImpl {
  private val sync = new AnyRef

  def addFactory(f: ListObjView.Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeID
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[ListObjView.Factory] = map.values

  def apply[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx): ListObjView[S] = {
    val tid = obj.tpe.typeID
    // getOrElse(sys.error(s"No view for type $tid"))
    map.get(tid).fold[ListObjView[S]](GenericObjView.mkListView(obj)) { f =>
      f.mkListView(obj.asInstanceOf[f.E[S]])
    }
  }

  private var map = scala.Predef.Map[Int, ListObjView.Factory](
    ObjViewImpl.String          .tpe.typeID -> ObjViewImpl.String,
    IntObjView                  .tpe.typeID -> IntObjView,
    ObjViewImpl.Long            .tpe.typeID -> ObjViewImpl.Long,
    ObjViewImpl.Double          .tpe.typeID -> ObjViewImpl.Double,
    ObjViewImpl.IntVector       .tpe.typeID -> ObjViewImpl.IntVector,
    ObjViewImpl.DoubleVector    .tpe.typeID -> ObjViewImpl.DoubleVector,
    ObjViewImpl.Boolean         .tpe.typeID -> ObjViewImpl.Boolean,
    ObjViewImpl.Color           .tpe.typeID -> ObjViewImpl.Color,
    AudioCueObjView             .tpe.typeID -> AudioCueObjView,
    ArtifactLocationObjView     .tpe.typeID -> ArtifactLocationObjView,
    ArtifactObjView             .tpe.typeID -> ArtifactObjView,
//    FScapeObjView               .tpe.typeID -> FScapeObjView,
    ObjViewImpl.Folder          .tpe.typeID -> ObjViewImpl.Folder,
    ProcObjView                 .tpe.typeID -> ProcObjView,
    ObjViewImpl.Timeline        .tpe.typeID -> ObjViewImpl.Timeline,
    ObjViewImpl.Grapheme        .tpe.typeID -> ObjViewImpl.Grapheme,
    CodeObjView                 .tpe.typeID -> CodeObjView,
    ObjViewImpl.FadeSpec        .tpe.typeID -> ObjViewImpl.FadeSpec,
    ActionView                  .tpe.typeID -> ActionView,
    ObjViewImpl.Ensemble        .tpe.typeID -> ObjViewImpl.Ensemble,
    ObjViewImpl.Nuages          .tpe.typeID -> ObjViewImpl.Nuages,
    OutputObjView               .tpe.typeID -> OutputObjView,
    MarkdownObjView             .tpe.typeID -> MarkdownObjView,
    ParamSpecObjView            .tpe.typeID -> ParamSpecObjView
  )

  /** A trait that when mixed in provides `isEditable` and `tryEdit` as non-op methods. */
  trait NonEditable[S <: stm.Sys[S]] extends ListObjView[S] {
    def isEditable: Boolean = false

    def tryEdit(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = None
  }

  trait EmptyRenderer[S <: stm.Sys[S]] {
    def configureRenderer(label: Label): Component = label
    // def isUpdateVisible(update: Any)(implicit tx: S#Tx): Boolean = false
    def value: Any = ()
  }

  trait StringRenderer {
    def value: Any

    def configureRenderer(label: Label): Component = {
      label.text = value.toString
      label
    }
  }

  trait ExprLike[S <: stm.Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ListObjView[S] {
    protected var exprValue: A

    // def obj: stm.Source[S#Tx, Obj.T[S, Elem { type Peer = Expr[S, A] }]]

    // /** Tests a value from a `Change` update. */
    // protected def testValue       (v: Any): Option[A]
    protected def convertEditValue(v: Any): Option[A]

    protected implicit val exprType: Type.Expr[A, Ex]

    protected def expr(implicit tx: S#Tx): Ex[S]

    def tryEdit(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] =
      convertEditValue(value).flatMap { newValue =>
        expr match {
          case exprType.Var(vr) =>
            import de.sciss.equal.Implicits._
            vr() match {
              case Expr.Const(x) if x === newValue => None
              case _ =>
                // val imp = ExprImplicits[S]
                // import imp._
                // vr() = newValue
//                implicit val ser    = exprType.serializer   [S]
//                implicit val serVr  = exprType.varSerializer[S]
                val ed = EditVar.Expr[S, A, Ex](s"Change $humanName Value", vr, exprType.newConst[S](newValue))
                Some(ed)
            }

          case _ => None
        }
      }

//    def isUpdateVisible(update: Any)(implicit tx: S#Tx): Boolean = update match {
//      case Change(_, now) =>
//        testValue(now).exists { valueNew =>
//          deferTx {
//            exprValue = valueNew
//          }
//          true
//        }
//      case _ => false
//    }

    // XXX TODO - this is a quick hack for demo
    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): Option[Window[S]] = {
      workspace match {
        case cf: Workspace.Confluent =>
          // XXX TODO - all this casting is horrible
          implicit val ctx: Confluent#Tx = tx.asInstanceOf[Confluent#Tx]
          implicit val ser: Serializer[Confluent#Tx, Access[Cf], Ex[Cf]] = exprType.serializer[Confluent]
          val name = AttrCellView.name[Confluent](obj.asInstanceOf[Obj[Confluent]])
            .map(n => s"History for '$n'")
          val w = new WindowImpl[Confluent](name) {
            val view: ViewHasWorkspace[Confluent] = ExprHistoryView[A, Ex](cf, expr.asInstanceOf[Ex[Confluent]])
            init()
          }
          Some(w.asInstanceOf[Window[S]])
        case _ => None
      }
    }
  }

  trait SimpleExpr[S <: Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ExprLike[S, A, Ex]
    with ListObjView[S] with ObjViewImpl.Impl[S] {
    // _: ObjView[S] =>

    override  def value: A
    protected def value_=(x: A): Unit

    protected def exprValue: A = value
    protected def exprValue_=(x: A): Unit = value = x

    def init(ex: Ex[S])(implicit tx: S#Tx): this.type = {
      initAttrs(ex)
      disposables ::= ex.changed.react { implicit tx => upd =>
        deferTx {
          exprValue = upd.now
        }
        fire(ObjView.Repaint(this))
      }
      this
    }
  }

  private final val ggCheckBox = new CheckBox()

  trait BooleanExprLike[S <: Sys[S]] extends ExprLike[S, Boolean, BooleanObj] {
    _: ListObjView[S] =>

    val exprType: Type.Expr[Boolean, BooleanObj] = BooleanObj

    def convertEditValue(v: Any): Option[Boolean] = v match {
      case num: Boolean  => Some(num)
      case s: String     => Try(s.toBoolean).toOption
    }

    def testValue(v: Any): Option[Boolean] = v match {
      case i: Boolean  => Some(i)
      case _            => None
    }

    def configureRenderer(label: Label): Component = {
      ggCheckBox.selected   = exprValue
      ggCheckBox.background = label.background
      ggCheckBox
    }
  }
}
