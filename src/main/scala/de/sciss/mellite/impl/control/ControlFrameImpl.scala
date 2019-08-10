/*
 *  ControlFrameImpl.scala
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

package de.sciss.mellite.impl.control

import de.sciss.desktop.UndoManager
import de.sciss.lucre.expr.{BooleanObj, CellView}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.impl.code.CodeFrameBase
import de.sciss.mellite.{CodeView, ControlEditorFrame, ControlEditorView}
import de.sciss.synth.proc.{Control, Universe}

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.Action

object ControlFrameImpl {
  def editor[S <: Sys[S]](obj: Control[S], bottom: ISeq[View[S]])
                         (implicit tx: S#Tx, universe: Universe[S]): ControlEditorFrame[S] = {
    implicit val undo: UndoManager = UndoManager()
    val showEditor  = obj.attr.$[BooleanObj](Control.attrEditMode).forall(_.value)
    val view        = ControlEditorView(obj, showEditor = showEditor, bottom = bottom)
    val res         = new EditorFrameImpl[S](view, tx).init()
//    trackTitle(res, view.renderer)
    setTitle(res, view.control)
    res
  }

//  def render[S <: Sys[S]](obj: Control[S])
//                         (implicit tx: S#Tx, universe: Universe[S]): ControlRenderFrame[S] = {
//    implicit val undoManagerTx: stm.UndoManager[S] = stm.UndoManager()
//    val view  = ControlRenderView(obj)
//    val res   = new RenderFrameImpl[S](view).init()
//    trackTitle(res, view)
//    res
//  }

  private def setTitle[S <: Sys[S]](win: WindowImpl[S], md: Control[S])(implicit tx: S#Tx): Unit =
    win.setTitleExpr(Some(CellView.name(md)))

//  private def trackTitle[S <: Sys[S]](win: WindowImpl[S], renderer: ControlRenderView[S])(implicit tx: S#Tx): Unit = {
//    setTitle(win, renderer.widget)
//  }

  // ---- frame impl ----

  //  private abstract class FrameBase[S <: Sys[S]] extends WindowImpl[S] {
  //    protected def renderer: ControlRenderView[S]
  //
  //    override protected def initGUI(): Unit = {
  //      super.initGUI()
  //
  //    }
  //  }

//  private final class RenderFrameImpl[S <: Sys[S]](val view: ControlRenderView[S])
//    extends WindowImpl[S] with ControlRenderFrame[S] {
//
//    //    protected def renderer: ControlRenderView[S] = view
//  }

  private final class EditorFrameImpl[S <: Sys[S]](val view: ControlEditorView[S], tx0: S#Tx)
    extends WindowImpl[S] with CodeFrameBase[S] with ControlEditorFrame[S] {

    protected def codeView: CodeView[S, _]      = view.codeView
//    protected def renderer: ControlRenderView[S] = view.renderer

    private[this] var rUndoName   = Option.empty[String]
    private[this] var rRedoName   = Option.empty[String]

//    private[this] val rUndoObs = renderer.undoManager.react { implicit tx => upd =>
//      deferTx {
//        rUndoName = upd.undoName
//        rRedoName = upd.redoName
//        val tb = view.currentTab
//        if (tb == ControlEditorView.RendererTab) {
//          undoAction.update(tb)
//          redoAction.update(tb)
//        }
//      }
//    } (tx0)

    override def dispose()(implicit tx: S#Tx): Unit = {
      super.dispose()
//      rUndoObs.dispose()
    }

    override protected def initGUI(): Unit = {
      super.initGUI()
      mkExamplesMenu(Control.Code.examples)
      view.addListener {
        case ControlEditorView.TabChange(tb) =>
          undoAction.update(tb)
          redoAction.update(tb)
      }
    }

    private object undoAction extends Action("Undo") {
      enabled = false

      def update(tb: ControlEditorView.Tab): Unit = {
        val undoName = tb match {
          case ControlEditorView.EditorTab   => None // XXX TODO
          case ControlEditorView.RendererTab => rUndoName
        }
        enabled = rUndoName.isDefined
        text    = undoName.fold("Undo")(n => s"Undo $n")
      }

      def apply(): Unit =
        view.currentTab match {
          case ControlEditorView.EditorTab   => () // XXX TODO
          case ControlEditorView.RendererTab =>
//            val u = renderer.undoManager
//            cursor.step { implicit tx =>
//              if (u.canUndo) u.undo()
//            }
        }
    }

    private object redoAction extends Action("Redo") {
      enabled = false

      def update(tb: ControlEditorView.Tab): Unit = {
        val redoName = tb match {
          case ControlEditorView.EditorTab   => None // XXX TODO
          case ControlEditorView.RendererTab => rRedoName
        }
        enabled = rRedoName.isDefined
        text    = redoName.fold("Redo")(n => s"Redo $n")
      }

      def apply(): Unit =
        view.currentTab match {
          case ControlEditorView.EditorTab   => () // XXX TODO
          case ControlEditorView.RendererTab =>
//            val u = renderer.undoManager
//            cursor.step { implicit tx =>
//              if (u.canRedo) u.redo()
//            }
        }
    }

    override protected def undoRedoActions: Option[(Action, Action)] = {
      Some((undoAction, redoAction))
    }
  }
}