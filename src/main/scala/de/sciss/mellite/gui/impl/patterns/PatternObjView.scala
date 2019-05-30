/*
 *  PatternObjView.scala
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

package de.sciss.mellite.gui.impl.patterns

import de.sciss.desktop.UndoManager
import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Plain}
import de.sciss.lucre.swing._
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.code.CodeFrameImpl
import de.sciss.mellite.gui.impl.objview.ObjListViewImpl.NonEditable
import de.sciss.mellite.gui.impl.objview.{NoArgsListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.impl.timeline.ObjTimelineViewBasicImpl
import de.sciss.mellite.gui.{CodeFrame, CodeView, GUI, ObjListView, ObjTimelineView, ObjView, PlayToggleButton, Shapes}
import de.sciss.patterns
import de.sciss.patterns.Pat
import de.sciss.patterns.lucre.Pattern
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.Button

object PatternObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: stm.Sys[~]] = Pattern[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Pattern)
  val prefix        : String    = "Pattern"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Pattern
  def category      : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Pattern[S])(implicit tx: S#Tx): PatternObjView[S] with ObjListView[S] = {
//    val vr = Pattern.Var.unapply(obj).getOrElse {
//      val _vr = Pattern.newVar[S](obj)
//      _vr
//    }
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)
  }

  def mkTimelineView[S <: Sys[S]](id: S#Id, span: SpanLikeObj[S], obj: Pattern[S],
                                  context: ObjTimelineView.Context[S])(implicit tx: S#Tx): ObjTimelineView[S] = {
    val res = new TimelineImpl[S](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Pattern[S]])
    extends Impl[S]

  private final class TimelineImpl[S <: Sys[S]](val objH : stm.Source[S#Tx, Pattern[S]])
    extends Impl[S] with ObjTimelineViewBasicImpl[S]

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Pattern.newVar[S](Pattern.empty[S])
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private abstract class Impl[S <: Sys[S]]
    extends PatternObjView[S]
      with ObjListView /* .Int */[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.EmptyRenderer[S]
      with NonEditable[S]
      /* with NonViewable[S] */ {

    override def objH: stm.Source[S#Tx, Pattern[S]]

    override def obj(implicit tx: S#Tx): Pattern[S] = objH()

    type E[~ <: stm.Sys[~]] = Pattern[~]

    final def factory: ObjView.Factory = PatternObjView

    final def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    final def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      Pattern.Var.unapply(obj).map { vr =>
        import de.sciss.mellite.Mellite.compiler
        val frame = codeFrame(vr)
        frame
      }
    }

    // ---- adapter for editing an Pattern's source ----
  }

  private def codeFrame[S <: Sys[S]](obj: Pattern.Var[S])
                                    (implicit tx: S#Tx, universe: Universe[S],
                                     compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = CodeFrameImpl.mkSource(obj = obj, codeTpe = Pattern.Code, key = Pattern.attrSource)()
    val objH    = tx.newHandle(obj) // IntelliJ highlight bug
    val code0   = codeObj.value match {
      case cs: Pattern.Code => cs
      case other => sys.error(s"Pattern source code does not produce patterns.Graph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[S, Unit, Pat[_]] {
      def in(): Unit = ()

      def save(in: Unit, out: Pat[_])(implicit tx: S#Tx): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[S, Pat[_], Pattern]("Change Pattern Graph", obj, Pattern.newConst[S](out))
      }

      def dispose()(implicit tx: S#Tx): Unit = ()
    }

    val viewEval = View.wrap[S, Button] {
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

    val viewPower = PlayToggleButton(obj)

    val bottom = viewEval :: viewPower :: Nil

    implicit val undo: UndoManager = UndoManager()
    CodeFrameImpl.make(obj, objH, codeObj, code0, Some(handler), bottom = bottom, rightViewOpt = None,
      canBounce = true)
  }
}
trait PatternObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Pattern[S]
}