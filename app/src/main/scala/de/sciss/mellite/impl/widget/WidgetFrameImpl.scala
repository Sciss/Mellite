/*
 *  WidgetFrameImpl.scala
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

package de.sciss.mellite.impl.widget

import de.sciss.desktop.UndoManager
import de.sciss.lucre.expr.{BooleanObj, CellView}
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.CodeView
import de.sciss.mellite.impl.code.CodeFrameBase
import de.sciss.mellite.{WidgetEditorFrame, WidgetEditorView, WidgetRenderFrame, WidgetRenderView}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.synth.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.Action

object WidgetFrameImpl {
  def editor[T <: Txn[T]](obj: Widget[T], bottom: ISeq[View[T]])
                         (implicit tx: T, universe: Universe[T]): WidgetEditorFrame[T] = {
    implicit val undo: UndoManager = UndoManager()
    val showEditor  = obj.attr.$[BooleanObj](Widget.attrEditMode).forall(_.value)
    val view        = WidgetEditorView(obj, showEditor = showEditor, bottom = bottom)
    val res         = new EditorFrameImpl[T](view, tx).init()
    trackTitle(res, view.renderer)
    res
  }

  def render[T <: Txn[T]](obj: Widget[T])
                         (implicit tx: T, universe: Universe[T]): WidgetRenderFrame[T] = {
    implicit val undoManagerTx: stm.UndoManager[T] = stm.UndoManager()
    val view  = WidgetRenderView(obj)
    val res   = new RenderFrameImpl[T](view).init()
    trackTitle(res, view)
    res
  }

  private def setTitle[T <: Txn[T]](win: WindowImpl[T], md: Widget[T])(implicit tx: T): Unit =
    win.setTitleExpr(Some(CellView.name(md)))

  private def trackTitle[T <: Txn[T]](win: WindowImpl[T], renderer: WidgetRenderView[T])(implicit tx: T): Unit = {
    setTitle(win, renderer.widget)
//    renderer.react { implicit tx => {
//      case WidgetRenderView.FollowedLink(_, now) => setTitle(win, now)
//    }}
  }

  // ---- frame impl ----

//  private abstract class FrameBase[T <: Txn[T]] extends WindowImpl[T] {
//    protected def renderer: WidgetRenderView[T]
//
//    override protected def initGUI(): Unit = {
//      super.initGUI()
//
//    }
//  }

  private final class RenderFrameImpl[T <: Txn[T]](val view: WidgetRenderView[T])
    extends WindowImpl[T] with WidgetRenderFrame[T] {

//    protected def renderer: WidgetRenderView[T] = view
  }

  private final class EditorFrameImpl[T <: Txn[T]](val view: WidgetEditorView[T], tx0: T)
    extends WindowImpl[T] with CodeFrameBase[T] with WidgetEditorFrame[T] {
    
    import view.cursor

    protected def codeView: CodeView[T, _]      = view.codeView
    protected def renderer: WidgetRenderView[T] = view.renderer
    
    private[this] var rUndoName   = Option.empty[String]
    private[this] var rRedoName   = Option.empty[String]

    private[this] val rUndoObs = renderer.undoManager.react { implicit tx => upd =>
      deferTx {
        rUndoName = upd.undoName
        rRedoName = upd.redoName
        val tb = view.currentTab
        if (tb == WidgetEditorView.RendererTab) {
          undoAction.update(tb)
          redoAction.update(tb)
        }
      }
    } (tx0)

    override def dispose()(implicit tx: T): Unit = {
      super.dispose()
      rUndoObs.dispose()
    }

    override protected def initGUI(): Unit = {
      super.initGUI()
      mkExamplesMenu(Widget.Code.examples)
      view.addListener {
        case WidgetEditorView.TabChange(tb) =>
          undoAction.update(tb)
          redoAction.update(tb)
      }
    }

    private object undoAction extends Action("Undo") {
      enabled = false

      def update(tb: WidgetEditorView.Tab): Unit = {
        val undoName = tb match {
          case WidgetEditorView.EditorTab   => None // XXX TODO
          case WidgetEditorView.RendererTab => rUndoName
        }
        enabled = rUndoName.isDefined
        text    = undoName.fold("Undo")(n => s"Undo $n")
      }

      def apply(): Unit =
        view.currentTab match {
          case WidgetEditorView.EditorTab   => () // XXX TODO
          case WidgetEditorView.RendererTab =>
            val u = renderer.undoManager
            cursor.step { implicit tx =>
              if (u.canUndo) u.undo()
            }
        }
    }

    private object redoAction extends Action("Redo") {
      enabled = false

      def update(tb: WidgetEditorView.Tab): Unit = {
        val redoName = tb match {
          case WidgetEditorView.EditorTab   => None // XXX TODO
          case WidgetEditorView.RendererTab => rRedoName
        }
        enabled = rRedoName.isDefined
        text    = redoName.fold("Redo")(n => s"Redo $n")
      }

      def apply(): Unit =
        view.currentTab match {
          case WidgetEditorView.EditorTab   => () // XXX TODO
          case WidgetEditorView.RendererTab =>
            val u = renderer.undoManager
            cursor.step { implicit tx =>
              if (u.canRedo) u.redo()
            }
        }
    }

    override protected def undoRedoActions: Option[(Action, Action)] = {
      Some((undoAction, redoAction))
    }
  }
}