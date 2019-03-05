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

package de.sciss.mellite
package gui.impl.patterns

import de.sciss.desktop.UndoManager
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Obj, Plain}
import de.sciss.lucre.swing._
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.code.CodeFrameImpl
import de.sciss.mellite.gui.impl.objview.ListObjViewImpl.NonEditable
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, NoArgsListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.gui.{CodeFrame, CodeView, GUI, ListObjView, ObjView, PlayToggleButton, Shapes}
import de.sciss.patterns
import de.sciss.patterns.Pat
import de.sciss.patterns.lucre.Pattern
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.Button

object PatternObjView extends NoArgsListObjViewFactory {
  type E[~ <: stm.Sys[~]] = Pattern[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Pattern)
  val prefix        : String    = "Pattern"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Pattern
  def category      : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Pattern[S])(implicit tx: S#Tx): PatternObjView[S] with ListObjView[S] = {
    val vr = Pattern.Var.unapply(obj).getOrElse {
      val _vr = Pattern.newVar[S](obj)
      _vr
    }
    new Impl(tx.newHandle(vr)).initAttrs(obj)
  }

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Pattern.newVar[S](Pattern.empty[S])
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Pattern.Var[S]])
    extends PatternObjView[S]
      with ListObjView /* .Int */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.EmptyRenderer[S]
      with NonEditable[S]
      /* with NonViewable[S] */ {

    override def obj(implicit tx: S#Tx): Pattern.Var[S] = objH()

    type E[~ <: stm.Sys[~]] = Pattern[~]

    def factory: ObjView.Factory = PatternObjView

    def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = codeFrame(obj)
      Some(frame)
    }

    // ---- adapter for editing an Pattern's source ----
  }

  private def codeFrame[S <: Sys[S]](obj: Pattern.Var[S])
                                    (implicit tx: S#Tx, universe: Universe[S],
                                     compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = CodeFrameImpl.mkSource(obj = obj, codeId = Pattern.Code.id, key = Pattern.attrSource,
      init = "// Pattern graph function source code\n\n")

    val codeEx0 = codeObj
    val objH    = tx.newHandle(obj)
    val code0   = codeEx0.value match {
      case cs: Pattern.Code => cs
      case other => sys.error(s"Pattern source code does not produce patterns.Graph: ${other.contextName}")
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

    // XXX TODO --- should use custom view so we can cancel upon `dispose`
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
  override def obj(implicit tx: S#Tx): Pattern[S]
}