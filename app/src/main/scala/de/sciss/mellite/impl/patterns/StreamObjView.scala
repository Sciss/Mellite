/*
 *  StreamObjView.scala
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

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing._
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.edit.EditStreamPeer
import de.sciss.mellite.impl.code.CodeFrameImpl
import de.sciss.mellite.impl.objview.ObjListViewImpl.NonEditable
import de.sciss.mellite.impl.objview.{NoArgsListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.impl.timeline.ObjTimelineViewBasicImpl
import de.sciss.mellite.{CodeFrame, CodeView, GUI, ObjListView, ObjTimelineView, ObjView, RunnerToggleButton, Shapes}
import de.sciss.patterns
import de.sciss.patterns.Pat
import de.sciss.patterns.lucre.{Context, Pattern, Stream}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.{Icon, SpinnerNumberModel}
import javax.swing.undo.UndoableEdit

import scala.swing.Button
import scala.swing.event.Key

object StreamObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: stm.Sys[~]] = Stream[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Stream)
  val prefix        : String    = "Stream"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Stream
  def category      : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Stream[S])(implicit tx: S#Tx): StreamObjView[S] with ObjListView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  def mkTimelineView[S <: Sys[S]](id: S#Id, span: SpanLikeObj[S], obj: Stream[S],
                                  context: ObjTimelineView.Context[S])(implicit tx: S#Tx): ObjTimelineView[S] = {
    val res = new TimelineImpl[S](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Stream[S]])
    extends Impl[S]

  private final class TimelineImpl[S <: Sys[S]](val objH : stm.Source[S#Tx, Stream[S]])
    extends Impl[S] with ObjTimelineViewBasicImpl[S]

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Stream[S]() // .newVar[S](Stream.empty[S])
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private abstract class Impl[S <: Sys[S]]
    extends StreamObjView[S]
      with ObjListView[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.EmptyRenderer[S]
      with NonEditable[S] {

    override def objH: stm.Source[S#Tx, Stream[S]]

    override def obj(implicit tx: S#Tx): Stream[S] = objH()

    type E[~ <: stm.Sys[~]] = Stream[~]

    final def factory: ObjView.Factory = StreamObjView

    final def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    final def openView(parent: Option[Window[S]])
                      (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = codeFrame(obj)
      Some(frame)
    }
  }

  private def codeFrame[S <: Sys[S]](obj: Stream[S])
                                    (implicit tx: S#Tx, universe: Universe[S],
                                     compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = CodeFrameImpl.mkSource(obj = obj, codeTpe = Pattern.Code, key = Stream.attrSource)()
    val objH    = tx.newHandle(obj) // IntelliJ highlight bug
    val code0   = codeObj.value match {
      case cs: Pattern.Code => cs
      case other => sys.error(s"Stream source code does not produce patterns.Graph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[S, Unit, Pat[_]] {
      def in(): Unit = ()

      def save(in: Unit, out: Pat[_])(implicit tx: S#Tx): UndoableEdit = {
        val obj = objH()
        implicit val ctx: patterns.Context[S] = Context[S](tx.system, tx)
        import universe.cursor
        val v   = out.expand[S]
        EditStreamPeer[S]("Change Stream Graph", obj, v)
      }

      def dispose()(implicit tx: S#Tx): Unit = ()
    }

    val viewReset = View.wrap[S, Button] {
      val actionReset = new swing.Action("Reset") { self =>
        import universe.cursor
        def apply(): Unit = {
          cursor.step { implicit tx =>
            val obj = objH()
            val st  = obj.peer()
            st.reset()
          }
        }
      }
      val ks = KeyStrokes.menu1 + Key.F5
      val b = GUI.toolButton(actionReset, raphael.Shapes.Undo, tooltip =
        s"Reset stream to initial position (${GUI.keyStrokeText(ks)})")
      Util.addGlobalKey(b, ks)
      b
    }

    lazy val mEvalN = new SpinnerNumberModel(1, 1, 1000, 1)

    val viewEval = View.wrap[S, Button] {
      val actionEval = new swing.Action("Next") { self =>
        import universe.cursor
        def apply(): Unit = {
          val n = mEvalN.getNumber.intValue()
          val res = cursor.step { implicit tx =>
            implicit val ctx: patterns.Context[S] = patterns.lucre.Context[S](tx.system, tx)
            val obj = objH()
            val st  = obj.peer()
            if (n == 1) {
              if (st.hasNext) st.next().toString else "<EOS>"
            } else {
              val it = st.toIterator.take(n)
              it.mkString("[", ", ", "]")
            }
          }
          println(res)
        }
      }
      val ks = KeyStrokes.plain + Key.F8
      val b = GUI.toolButton(actionEval, raphael.Shapes.Quote,
        tooltip = s"Print next elements (${GUI.keyStrokeText(ks)})")
      Util.addGlobalKey(b, ks)
      b
    }

    val viewEvalN = View.wrap[S, Spinner] {
      val res = new Spinner(mEvalN)
      res.tooltip = "Number of elements to print"
      res
    }

    val viewPower = RunnerToggleButton(obj)

    val bottom = viewReset :: viewEval :: viewEvalN :: viewPower :: Nil

    implicit val undo: UndoManager = UndoManager()
    CodeFrameImpl.make(obj, objH, codeObj, code0, Some(handler), bottom = bottom, rightViewOpt = None,
      canBounce = true)
  }
}
trait StreamObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Stream[S]
}