/*
 *  PatternObjView.scala
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

package de.sciss.mellite.impl.patterns

import de.sciss.desktop.UndoManager
import de.sciss.icons.raphael
import de.sciss.lucre.swing._
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Ident, Obj, Plain, Source, SpanLikeObj, Txn => LTxn}
import de.sciss.mellite.impl.code.CodeFrameImpl
import de.sciss.mellite.impl.objview.ObjListViewImpl.NonEditable
import de.sciss.mellite.impl.objview.{NoArgsListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.impl.timeline.ObjTimelineViewBasicImpl
import de.sciss.mellite.{CodeFrame, CodeView, GUI, ObjListView, ObjTimelineView, ObjView, RunnerToggleButton, Shapes}
import de.sciss.patterns
import de.sciss.patterns.graph.Pat
import de.sciss.proc.Pattern
import de.sciss.proc.{Code, Universe}
import de.sciss.proc.Implicits._
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.Button

object PatternObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: LTxn[~]] = Pattern[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Pattern)
  val prefix        : String    = "Pattern"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Pattern
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Pattern[T])(implicit tx: T): PatternObjView[T] with ObjListView[T] = {
//    val vr = Pattern.Var.unapply(obj).getOrElse {
//      val _vr = Pattern.newVar[T](obj)
//      _vr
//    }
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)
  }

  def mkTimelineView[T <: Txn[T]](id: Ident[T], span: SpanLikeObj[T], obj: Pattern[T],
                                  context: ObjTimelineView.Context[T])(implicit tx: T): ObjTimelineView[T] = {
    val res = new TimelineImpl[T](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, Pattern[T]])
    extends Impl[T]

  private final class TimelineImpl[T <: Txn[T]](val objH : Source[T, Pattern[T]])
    extends Impl[T] with ObjTimelineViewBasicImpl[T]

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj  = Pattern.newVar[T](Pattern.empty[T])
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private abstract class Impl[T <: Txn[T]]
    extends PatternObjView[T]
      with ObjListView /* .Int */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.EmptyRenderer[T]
      with NonEditable[T]
      /* with NonViewable[T] */ {

    override def objH: Source[T, Pattern[T]]

    override def obj(implicit tx: T): Pattern[T] = objH()

    type E[~ <: LTxn[~]] = Pattern[~]

    final def factory: ObjView.Factory = PatternObjView

    final def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    final def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      Pattern.Var.unapply(obj).map { vr =>
        import de.sciss.mellite.Mellite.compiler
        val frame = codeFrame(vr)
        frame
      }
    }

    // ---- adapter for editing an Pattern's source ----
  }

  private def codeFrame[T <: Txn[T]](obj: Pattern.Var[T])
                                    (implicit tx: T, universe: Universe[T],
                                     compiler: Code.Compiler): CodeFrame[T] = {
    val codeObj = CodeFrameImpl.mkSource(obj = obj, codeTpe = Pattern.Code, key = Pattern.attrSource)()
    val objH    = tx.newHandle(obj) // IntelliJ highlight bug
    val code0   = codeObj.value match {
      case cs: Pattern.Code => cs
      case other => sys.error(s"Pattern source code does not produce patterns.Graph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[T, Unit, Pat[_]] {
      def in(): Unit = ()

      def save(in: Unit, out: Pat[_])(implicit tx: T): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[T, Pat[_], Pattern]("Change Pattern Graph", obj, Pattern.newConst[T](out))
      }

      def dispose()(implicit tx: T): Unit = ()
    }

    val viewEval = View.wrap[T, Button] {
      val actionEval = new swing.Action("Evaluate") { self =>
        import universe.cursor
        def apply(): Unit = {
          implicit val ctx: patterns.Context[Plain] = patterns.Context()
          val n = 60
          val res0 = cursor.step { implicit tx =>
            val obj = objH()
            val g   = obj.value
            val st  = g.expand
            st.toIterator.take(n).toList
          }
          val abbr  = res0.lengthCompare(n) == 0
          val res   = if (abbr) res0.init else res0
          println(res.mkString("[", ", ", if (abbr) " ...]" else "]"))
        }
      }
      GUI.toolButton(actionEval, raphael.Shapes.Quote)
    }

    val viewPower = RunnerToggleButton(obj)

    val bottom = viewEval :: viewPower :: Nil

    implicit val undo: UndoManager = UndoManager()
    CodeFrameImpl.make(obj, objH, codeObj, code0, Some(handler), bottom = bottom, rightViewOpt = None,
      canBounce = true)
  }
}
trait PatternObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Pattern[T]
}