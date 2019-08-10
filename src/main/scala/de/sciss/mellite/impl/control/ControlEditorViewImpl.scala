///*
// *  ControlViewImpl.scala
// *  (Mellite)
// *
// *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU Affero General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.mellite.impl.control
//
//import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
//import de.sciss.icons.raphael
//import de.sciss.lucre.stm
//import de.sciss.lucre.swing.LucreSwing.deferTx
//import de.sciss.lucre.swing.View
//import de.sciss.lucre.swing.edit.EditVar
//import de.sciss.lucre.swing.impl.ComponentHolder
//import de.sciss.lucre.synth.{Sys => SSys}
//import de.sciss.mellite.Mellite.executionContext
//import de.sciss.mellite.impl.code.CodeFrameImpl
//import de.sciss.mellite.{CodeView, GUI, ControlEditorView}
//import de.sciss.model.impl.ModelImpl
//import de.sciss.synth.proc.{Universe, Control}
//import javax.swing.undo.UndoableEdit
//
//import scala.collection.immutable.{Seq => ISeq}
//import scala.swing.event.{Key, SelectionChanged}
//import scala.swing.{Action, BorderPanel, Button, Component, TabbedPane}
//
//object ControlEditorViewImpl {
//  def apply[S <: SSys[S]](obj: Control[S], showEditor: Boolean, bottom: ISeq[View[S]])
//                         (implicit tx: S#Tx, universe: Universe[S],
//                          undoManager: UndoManager): ControlEditorView[S] = {
//    //    val editable = obj match {
//    //      case Control.Var(_) => true
//    //      case _               => false
//    //    }
//
//    implicit val undoManagerTx: stm.UndoManager[S] = stm.UndoManager()
////    val renderer  = ControlRenderView[S](obj, embedded = true)
//    val res       = new Impl[S](/*renderer,*/ tx.newHandle(obj), bottom = bottom)
//    res.init(obj, showEditor = showEditor)
//  }
//
//  private final class Impl[S <: SSys[S]](/*val renderer: ControlRenderView[S],*/
//                                         controlH: stm.Source[S#Tx, Control[S]],
//                                         bottom: ISeq[View[S]])
//                                        (implicit val universe: Universe[S],
//                                         undoManager: UndoManager, txUndo: stm.UndoManager[S])
//    extends ComponentHolder[Component] with ControlEditorView[S] with ModelImpl[ControlEditorView.Update] { impl =>
//
//    type C = Component
//
//    private[this] var _codeView: CodeView[S, Control.Graph] = _
//
//    def codeView: CodeView[S, Control.Graph] = _codeView
//
//    private[this] var actionRender: Action      = _
//    private[this] var tabs        : TabbedPane  = _
//
//    def dispose()(implicit tx: S#Tx): Unit = {
//      codeView.dispose()
////      renderer.dispose()
//      txUndo  .dispose()
//    }
//
//    def control(implicit tx: S#Tx): Control[S] = controlH()
//
//    private def renderAndShow(): Unit = {
//      //      val t = codeView.currentText
//      val fut = codeView.preview()
//      fut.foreach { g =>
//        cursor.step { implicit tx =>
//          ???
////          renderer.setGraph(g)
//        }
//      }
//      tabs.selection.index = 1
//    }
//
//    def init(obj: Control[S], /* initialText: String, */ showEditor: Boolean)(implicit tx: S#Tx): this.type = {
//      val codeObj = CodeFrameImpl.mkSource(obj = obj, codeTpe = Control.Code, key = Control.attrSource)()
//      val code0   = codeObj.value match {
//        case cs: Control.Code => cs
//        case other => sys.error(s"Control source code does not produce Control.Graph: ${other.tpe.humanName}")
//      }
//      val objH = tx.newHandle(obj)
//
//      val handler = new CodeView.Handler[S, Unit, Control.Graph] {
//        def in(): Unit = ()
//
//        def save(in: Unit, out: Control.Graph)(implicit tx: S#Tx): UndoableEdit = {
//          val obj = objH()
//          EditVar.Expr[S, Control.Graph, Control.GraphObj]("Change Control Graph", obj.graph, Control.GraphObj.newConst[S](out))
//        }
//
//        def dispose()(implicit tx: S#Tx): Unit = ()
//      }
//
//      val bot: View[S] = View.wrap {
//        actionRender  = Action(null   )(renderAndShow())
//        val ksRender  = KeyStrokes.shift + Key.F10
//        val ttRender  = s"Build (${Util.keyStrokeText(ksRender)})"
//
//        //      lazy val ggApply : Button = GUI.toolButton(actionApply , raphael.Shapes.Check       , tooltip = "Save text changes")
//        val ggRender: Button = GUI.toolButton(actionRender, raphael.Shapes.RefreshArrow, tooltip = ttRender)
//        Util.addGlobalKeyWhenVisible(ggRender, ksRender)
//        ggRender
//      }
//
//      import de.sciss.mellite.Mellite.compiler
//      _codeView = CodeView[S](codeObj, code0, bottom = bot +: bottom)(Some(handler))
//
//      deferTx(guiInit(/* initialText, */ showEditor = showEditor))
//      this
//    }
//
//    def currentTab: ControlEditorView.Tab =
//      if (tabs.selection.index == 0) ControlEditorView.EditorTab else ControlEditorView.RendererTab
//
//    private def guiInit(/* initialText: String, */ showEditor: Boolean): Unit = {
//      val paneEdit = new BorderPanel {
//        add(codeView.component, BorderPanel.Position.Center)
//        //        add(panelBottom       , BorderPanel.Position.South )
//      }
//
//      val _tabs = new TabbedPane
//      _tabs.peer.putClientProperty("styleId", "attached")
//      _tabs.focusable  = false
//      val pageEdit    = new TabbedPane.Page("Editor"   , paneEdit          , null)
////      val pageRender  = new TabbedPane.Page("Interface", renderer.component, null)
//      _tabs.pages     += pageEdit
////      _tabs.pages     += pageRender
//      //      _tabs.pages     += pageAttr
//      Util.addTabNavigation(_tabs)
//
//      _tabs.listenTo(_tabs.selection)
//      _tabs.reactions += {
//        case SelectionChanged(_) =>
//          impl.dispatch(ControlEditorView.TabChange(currentTab))
//      }
//
//      //      render(initialText)
//
//      tabs = _tabs
//
//      component = _tabs
//
//      if (showEditor) {
//        codeView.component.requestFocus()
//      } else {
////        _tabs.selection.index = 1
////        paneEdit.preferredSize = renderer.component.preferredSize
//      }
//    }
//  }
//}