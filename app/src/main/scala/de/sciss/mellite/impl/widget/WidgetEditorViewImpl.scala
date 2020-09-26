/*
 *  WidgetViewImpl.scala
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

import java.awt.event.{ComponentAdapter, ComponentEvent, ComponentListener}

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.Mellite.executionContext
import de.sciss.mellite.impl.code.CodeFrameImpl
import de.sciss.mellite.{CodeView, GUI, WidgetEditorView, WidgetRenderView}
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.{Universe, Widget}
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.event.{Key, SelectionChanged}
import scala.swing.{Action, BorderPanel, Button, Component, TabbedPane}

object WidgetEditorViewImpl {
  def apply[T <: SSys[T]](obj: Widget[T], showEditor: Boolean, bottom: ISeq[View[T]])
                         (implicit tx: T, universe: Universe[T],
                          undoManager: UndoManager): WidgetEditorView[T] = {
//    val editable = obj match {
//      case Widget.Var(_) => true
//      case _               => false
//    }

    implicit val undoManagerTx: stm.UndoManager[T] = stm.UndoManager()
    val renderer  = WidgetRenderView[T](obj, embedded = true)
    val res       = new Impl[T](renderer, tx.newHandle(obj), bottom = bottom)
    res.init(obj, showEditor = showEditor)
  }

  private final class Impl[T <: SSys[T]](val renderer: WidgetRenderView[T],
                                         widgetH: Source[T, Widget[T]],
                                         bottom: ISeq[View[T]])
                                        (implicit undoManager: UndoManager, txUndo: stm.UndoManager[T])
    extends ComponentHolder[Component] with WidgetEditorView[T] with ModelImpl[WidgetEditorView.Update] { impl =>

    type C = Component

    implicit val universe: Universe[T] = renderer.universe

    private[this] var _codeView: CodeView[T, Widget.Graph] = _

    def codeView: CodeView[T, Widget.Graph] = _codeView

    private[this] var actionRender: Action      = _
    private[this] var tabs        : TabbedPane  = _

    def dispose()(implicit tx: T): Unit = {
      codeView.dispose()
      renderer.dispose()
      txUndo  .dispose()
    }

    def widget(implicit tx: T): Widget[T] = widgetH()

    private def renderAndShow(): Unit = {
//      val t = codeView.currentText
      val fut = codeView.preview()
      fut.foreach { g =>
        cursor.step { implicit tx =>
          renderer.setGraph(g)
        }
      }
      tabs.selection.index = 1
    }

    def init(obj: Widget[T], /* initialText: String, */ showEditor: Boolean)(implicit tx: T): this.type = {
      val codeObj = CodeFrameImpl.mkSource(obj = obj, codeTpe = Widget.Code, key = Widget.attrSource)()
      val code0   = codeObj.value match {
        case cs: Widget.Code => cs
        case other => sys.error(s"Widget source code does not produce Widget.Graph: ${other.tpe.humanName}")
      }
      val objH = tx.newHandle(obj)

      val handler = new CodeView.Handler[T, Unit, Widget.Graph] {
        def in(): Unit = ()

        def save(in: Unit, out: Widget.Graph)(implicit tx: T): UndoableEdit = {
          val obj = objH()
          EditVar.Expr[T, Widget.Graph, Widget.GraphObj]("Change Widget Graph", obj.graph, Widget.GraphObj.newConst[T](out))
        }

        def dispose()(implicit tx: T): Unit = ()
      }

      val bot: View[T] = View.wrap {
        actionRender  = Action(null   )(renderAndShow())
        val ksRender  = KeyStrokes.shift + Key.F10
        val ttRender  = s"Build (${Util.keyStrokeText(ksRender)})"

        //      lazy val ggApply : Button = GUI.toolButton(actionApply , raphael.Shapes.Check       , tooltip = "Save text changes")
        val ggRender: Button = GUI.toolButton(actionRender, raphael.Shapes.RefreshArrow, tooltip = ttRender)
        Util.addGlobalKeyWhenVisible(ggRender, ksRender)
        ggRender
      }

      import de.sciss.mellite.Mellite.compiler
      _codeView = CodeView[T](codeObj, code0, bottom = bot +: bottom)(Some(handler))

      deferTx(guiInit(/* initialText, */ showEditor = showEditor))
      this
    }

    def currentTab: WidgetEditorView.Tab =
      if (tabs.selection.index == 0) WidgetEditorView.EditorTab else WidgetEditorView.RendererTab

    private def guiInit(/* initialText: String, */ showEditor: Boolean): Unit = {
      val paneEdit: BorderPanel = new BorderPanel {
        private def addEditor(): Unit =
          add(codeView.component, BorderPanel.Position.Center)

        if (showEditor) {
          addEditor()

        } else {
          lazy val cl: ComponentListener = new ComponentAdapter {
            override def componentShown(e: ComponentEvent): Unit = {
              if (peer.isShowing) {
                peer.removeComponentListener(cl)
                addEditor()
                revalidate()
              }
            }
          }
          peer.addComponentListener(cl)
        }
      }

      val _tabs = new TabbedPane
      _tabs.peer.putClientProperty("styleId", "attached")  // XXX TODO: obsolete
      _tabs.focusable  = false
      val pageEdit    = new TabbedPane.Page("Editor"   , paneEdit          , null)
      val pageRender  = new TabbedPane.Page("Interface", renderer.component, null)
      _tabs.pages     += pageEdit
      _tabs.pages     += pageRender
      //      _tabs.pages     += pageAttr
      Util.addTabNavigation(_tabs)

      _tabs.listenTo(_tabs.selection)
      _tabs.reactions += {
        case SelectionChanged(_) =>
          impl.dispatch(WidgetEditorView.TabChange(currentTab))
      }

      //      render(initialText)

      tabs = _tabs

      component = _tabs

      if (showEditor) {
        codeView.component.requestFocus()
      } else {
        _tabs.selection.index = 1
        paneEdit.preferredSize = renderer.component.preferredSize
      }
    }
  }
}