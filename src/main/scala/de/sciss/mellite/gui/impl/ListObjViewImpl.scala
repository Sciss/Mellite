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

import de.sciss.lucre.expr.{BooleanObj, Expr, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.artifact.{ArtifactLocationObjView, ArtifactObjView}
import de.sciss.mellite.gui.impl.audiocue.AudioCueObjView
import de.sciss.mellite.gui.impl.fscape.{FScapeObjView, FScapeOutputObjView}
import de.sciss.mellite.gui.impl.markdown.MarkdownObjView
import de.sciss.mellite.gui.impl.patterns.PatternObjView
import de.sciss.mellite.gui.impl.proc.{OutputObjView, ProcObjView}

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
    ActionView                .tpe.typeID -> ActionView,
    ArtifactLocationObjView   .tpe.typeID -> ArtifactLocationObjView,
    ArtifactObjView           .tpe.typeID -> ArtifactObjView,
    AudioCueObjView           .tpe.typeID -> AudioCueObjView,
    CodeObjView               .tpe.typeID -> CodeObjView,
    DoubleObjView             .tpe.typeID -> DoubleObjView,
    DoubleVectorObjView       .tpe.typeID -> DoubleVectorObjView,
    EnvSegmentObjView         .tpe.typeID -> EnvSegmentObjView,
    FreesoundRetrievalObjView .tpe.typeID -> FreesoundRetrievalObjView,
    FScapeObjView             .tpe.typeID -> FScapeObjView,
    FScapeOutputObjView       .tpe.typeID -> FScapeOutputObjView,
    IntObjView                .tpe.typeID -> IntObjView,
    MarkdownObjView           .tpe.typeID -> MarkdownObjView,
    ObjViewImpl.Boolean       .tpe.typeID -> ObjViewImpl.Boolean,
    ObjViewImpl.Color         .tpe.typeID -> ObjViewImpl.Color,
    ObjViewImpl.Ensemble      .tpe.typeID -> ObjViewImpl.Ensemble,
    ObjViewImpl.FadeSpec      .tpe.typeID -> ObjViewImpl.FadeSpec,
    ObjViewImpl.Folder        .tpe.typeID -> ObjViewImpl.Folder,
    ObjViewImpl.Grapheme      .tpe.typeID -> ObjViewImpl.Grapheme,
    ObjViewImpl.IntVector     .tpe.typeID -> ObjViewImpl.IntVector,
    ObjViewImpl.Long          .tpe.typeID -> ObjViewImpl.Long,
    ObjViewImpl.Nuages        .tpe.typeID -> ObjViewImpl.Nuages,
    ObjViewImpl.String        .tpe.typeID -> ObjViewImpl.String,
    ObjViewImpl.Timeline      .tpe.typeID -> ObjViewImpl.Timeline,
    OutputObjView             .tpe.typeID -> OutputObjView,
    ParamSpecObjView          .tpe.typeID -> ParamSpecObjView,
    PatternObjView            .tpe.typeID -> PatternObjView,
    ProcObjView               .tpe.typeID -> ProcObjView
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

  trait ExprLike[S <: stm.Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]]
    extends ObjViewImpl.ExprLike[S, A, Ex] with ListObjView[S] {

    protected var exprValue: A

    // /** Tests a value from a `Change` update. */
    // protected def testValue       (v: Any): Option[A]
    protected def convertEditValue(v: Any): Option[A]

    def tryEdit(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
      val tpe = exprType  // make IntelliJ happy
      convertEditValue(value).flatMap { newValue =>
        expr match {
          case tpe.Var(vr) =>
            import de.sciss.equal.Implicits._
            vr() match {
              case Expr.Const(x) if x === newValue => None
              case _ =>
                val ed = EditVar.Expr[S, A, Ex](s"Change $humanName Value", vr, tpe.newConst[S](newValue))
                Some(ed)
            }

          case _ => None
        }
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

    def exprType: Type.Expr[Boolean, BooleanObj] = BooleanObj

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
