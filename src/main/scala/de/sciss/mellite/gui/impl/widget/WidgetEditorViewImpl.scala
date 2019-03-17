/*
 *  WidgetViewImpl.scala
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
package gui.impl.widget

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.impl.code.CodeFrameImpl
import de.sciss.mellite.gui.{CodeView, GUI, WidgetEditorView, WidgetRenderView}
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.{Universe, Widget}
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, Button, Component, TabbedPane}

object WidgetEditorViewImpl {
  def apply[S <: SSys[S]](obj: Widget[S], showEditor: Boolean, bottom: ISeq[View[S]])
                         (implicit tx: S#Tx, universe: Universe[S],
                          undoManager: UndoManager): WidgetEditorView[S] = {
//    val editable = obj match {
//      case Widget.Var(_) => true
//      case _               => false
//    }

    val renderer  = WidgetRenderView[S](obj, embedded = true)
    val res       = new Impl[S](renderer, tx.newHandle(obj), bottom = bottom)
    res.init(obj, showEditor = showEditor)
  }

  private final class Impl[S <: SSys[S]](val renderer: WidgetRenderView[S],
                                         widgetH: stm.Source[S#Tx, Widget[S]],
                                         bottom: ISeq[View[S]])
                                        (implicit undoManager: UndoManager)
    extends ComponentHolder[Component] with WidgetEditorView[S] with ModelImpl[WidgetEditorView.Update] { impl =>

    type C = Component

    implicit val universe: Universe[S] = renderer.universe

    private[this] var _codeView: CodeView[S, Widget.Graph] = _

    def codeView: CodeView[S, Widget.Graph] = _codeView

    private[this] var actionRender: Action      = _
    private[this] var tabs        : TabbedPane  = _

    def dispose()(implicit tx: S#Tx): Unit = {
      codeView.dispose()
      renderer.dispose()
    }

    def widget(implicit tx: S#Tx): Widget[S] = widgetH()

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

    def init(obj: Widget[S], /* initialText: String, */ showEditor: Boolean)(implicit tx: S#Tx): this.type = {
      val codeObj = CodeFrameImpl.mkSource(obj = obj, codeId = Widget.Code.id, key = Widget.attrSource,
        init =
          """// Widget graph function source code
            |
            |Label("Foo")
            |""".stripMargin)

      val codeEx0   = codeObj
      val code0   = codeEx0.value match {
        case cs: Widget.Code => cs
        case other => sys.error(s"Widget source code does not produce Widget.Graph: ${other.tpe.humanName}")
      }
      val objH = tx.newHandle(obj)

      val handler = new CodeView.Handler[S, Unit, Widget.Graph] {
        def in(): Unit = ()

        def save(in: Unit, out: Widget.Graph)(implicit tx: S#Tx): UndoableEdit = {
          val obj = objH()
          EditVar.Expr[S, Widget.Graph, Widget.GraphObj]("Change Widget Graph", obj.graph, Widget.GraphObj.newConst[S](out))
        }

        def dispose()(implicit tx: S#Tx): Unit = ()
      }

      val bot: View[S] = View.wrap {
        actionRender  = Action(null   )(renderAndShow())
        val ksRender  = KeyStrokes.shift + Key.F10
        val ttRender  = s"Render (${Util.keyStrokeText(ksRender)})"

        //      lazy val ggApply : Button = GUI.toolButton(actionApply , raphael.Shapes.Check       , tooltip = "Save text changes")
        val ggRender: Button = GUI.toolButton(actionRender, raphael.Shapes.RefreshArrow, tooltip = ttRender)
        Util.addGlobalKeyWhenVisible(ggRender, ksRender)
        ggRender
      }

      import de.sciss.mellite.Mellite.compiler
      _codeView = CodeView[S](codeObj, code0, bottom = bot +: bottom)(Some(handler))

      deferTx(guiInit(/* initialText, */ showEditor = showEditor))
      this
    }

    private def guiInit(/* initialText: String, */ showEditor: Boolean): Unit = {
      val paneEdit = new BorderPanel {
        add(codeView.component, BorderPanel.Position.Center)
//        add(panelBottom       , BorderPanel.Position.South )
      }

      val _tabs = new TabbedPane
      _tabs.peer.putClientProperty("styleId", "attached")
      _tabs.focusable  = false
      val pageEdit    = new TabbedPane.Page("Editor"  , paneEdit          , null)
      val pageRender  = new TabbedPane.Page("Rendered", renderer.component, null)
      _tabs.pages     += pageEdit
      _tabs.pages     += pageRender
      //      _tabs.pages     += pageAttr
      Util.addTabNavigation(_tabs)

      //      render(initialText)

      tabs = _tabs

      component = _tabs

      if (showEditor) {
        codeView.component.requestFocus()
      } else {
        _tabs.selection.index = 1
      }
    }
  }
}