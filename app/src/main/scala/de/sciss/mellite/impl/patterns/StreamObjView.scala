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
import de.sciss.lucre.swing._
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Ident, Obj, Source, SpanLikeObj, Txn => LTxn}
import de.sciss.mellite.edit.EditStreamPeer
import de.sciss.mellite.impl.code.CodeFrameImpl
import de.sciss.mellite.impl.objview.ObjListViewImpl.NonEditable
import de.sciss.mellite.impl.objview.{NoArgsListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.impl.timeline.ObjTimelineViewBasicImpl
import de.sciss.mellite.{CodeFrame, CodeView, GUI, ObjListView, ObjTimelineView, ObjView, Shapes}
import de.sciss.patterns
import de.sciss.patterns.Pat
import de.sciss.patterns.lucre.{Pattern, Stream}
import de.sciss.swingplus.Spinner
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.undo.UndoableEdit
import javax.swing.{Icon, SpinnerNumberModel}

import scala.swing.Button
import scala.swing.event.Key

object StreamObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: LTxn[~]] = Stream[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Stream)
  val prefix        : String    = "Stream"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Stream
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Stream[T])(implicit tx: T): StreamObjView[T] with ObjListView[T] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  def mkTimelineView[T <: Txn[T]](id: Ident[T], span: SpanLikeObj[T], obj: Stream[T],
                                  context: ObjTimelineView.Context[T])(implicit tx: T): ObjTimelineView[T] = {
    val res = new TimelineImpl[T](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, Stream[T]])
    extends Impl[T]

  private final class TimelineImpl[T <: Txn[T]](val objH : Source[T, Stream[T]])
    extends Impl[T] with ObjTimelineViewBasicImpl[T]

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj  = Stream[T]() // .newVar[T](Stream.empty[T])
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private abstract class Impl[T <: Txn[T]]
    extends StreamObjView[T]
      with ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.EmptyRenderer[T]
      with NonEditable[T] {

    override def objH: Source[T, Stream[T]]

    override def obj(implicit tx: T): Stream[T] = objH()

    type E[~ <: LTxn[~]] = Stream[~]

    final def factory: ObjView.Factory = StreamObjView

    final def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    final def openView(parent: Option[Window[T]])
                      (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = codeFrame(obj)
      Some(frame)
    }
  }

  private def codeFrame[T <: Txn[T]](obj: Stream[T])
                                    (implicit tx: T, universe: Universe[T],
                                     compiler: Code.Compiler): CodeFrame[T] = {
    val codeObj = CodeFrameImpl.mkSource(obj = obj, codeTpe = Pattern.Code, key = Stream.attrSource)()
    val objH    = tx.newHandle(obj) // IntelliJ highlight bug
    val code0   = codeObj.value match {
      case cs: Pattern.Code => cs
      case other => sys.error(s"Stream source code does not produce patterns.Graph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[T, Unit, Pat[_]] {
      def in(): Unit = ()

      def save(in: Unit, out: Pat[_])(implicit tx: T): UndoableEdit = {
        val obj = objH()
        import obj.context
        val v   = out.expand[T]
        EditStreamPeer[T]("Change Stream Graph", obj, v)
      }

      def dispose()(implicit tx: T): Unit = ()
    }

    val viewReset = View.wrap[T, Button] {
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

    val viewEval = View.wrap[T, Button] {
      val actionEval = new swing.Action("Next") { self =>
        import universe.cursor
        def apply(): Unit = {
          val n = mEvalN.getNumber.intValue()
          val res = cursor.step { implicit tx =>
            implicit val ctx: patterns.Context[T] = patterns.lucre.Context[T](tx.system, tx)
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

    val viewEvalN = View.wrap[T, Spinner] {
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
trait StreamObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Stream[T]
}