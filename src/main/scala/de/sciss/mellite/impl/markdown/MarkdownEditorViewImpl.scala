/*
 *  MarkdownViewImpl.scala
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

package de.sciss.mellite.impl.markdown

import java.beans.{PropertyChangeEvent, PropertyChangeListener}

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.TxnLike
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.{GUI, MarkdownEditorView, MarkdownRenderView}
import de.sciss.model.impl.ModelImpl
import de.sciss.scalainterpreter.Fonts
import de.sciss.scalainterpreter.impl.CodePaneImpl
import de.sciss.swingplus.Implicits._
import de.sciss.syntaxpane.SyntaxDocument
import de.sciss.syntaxpane.syntaxkits.MarkdownSyntaxKit
import de.sciss.synth.proc.{Markdown, Universe}
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, Button, Component, EditorPane, FlowPanel, TabbedPane}

object MarkdownEditorViewImpl extends MarkdownEditorView.Companion {
  def install(): Unit =
    MarkdownEditorView.peer = this

  def apply[S <: SSys[S]](obj: Markdown[S], showEditor: Boolean, bottom: ISeq[View[S]])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undoManager: UndoManager): MarkdownEditorView[S] = {
    val editable = obj match {
      case Markdown.Var(_) => true
      case _               => false
    }
    val textValue = obj.value
    val renderer  = MarkdownRenderView[S](obj, embedded = true)
    val res       = new Impl[S](renderer, tx.newHandle(obj), editable = editable, bottom = bottom)
    res.init(textValue, showEditor = showEditor)
  }

  private def createPane(initialText: String): PaneImpl = {
    CodePaneImpl.initKit[MarkdownSyntaxKit]()
    new PaneImpl(initialText).init()
  }

  private final class PaneImpl(protected val initialText: String)
    extends CodePaneImpl.Basic {

    val editor                : EditorPane  = CodePaneImpl.createEditorPane()
    protected def mimeType    : String      = "text/markdown"
    protected def fonts       : Fonts.List  = Fonts.defaultFonts
    protected def tabSize     : Int         = 4
  }

  private final class Impl[S <: SSys[S]](val renderer: MarkdownRenderView[S],
                                         markdownH: stm.Source[S#Tx, Markdown[S]],
                                         editable: Boolean,
                                         bottom: ISeq[View[S]])
                                        (implicit undoManager: UndoManager)
    extends ComponentHolder[Component] with MarkdownEditorView[S] with ModelImpl[MarkdownEditorView.Update] { impl =>

    type C = Component

    implicit val universe: Universe[S] = renderer.universe

    private[this] val _dirty = Ref(false)

    def dirty(implicit tx: TxnLike): Boolean = _dirty.get(tx.peer)

    protected def dirty_=(value: Boolean): Unit = {
      requireEDT()
      val wasDirty = _dirty.single.swap(value)
      if (wasDirty != value) {
//        deferTx {
          actionApply.enabled = value
          dispatch(MarkdownEditorView.DirtyChange(value))
        }
//      }
    }

    private[this] var paneImpl    : PaneImpl    = _
    private[this] var actionApply : Action      = _
    private[this] var actionRender: Action      = _
    private[this] var tabs        : TabbedPane  = _

    protected def currentText: String = paneImpl.editor.text

    def dispose()(implicit tx: S#Tx): Unit = ()

    def undoAction: Action = Action.wrap(paneImpl.editor.peer.getActionMap.get("undo"))
    def redoAction: Action = Action.wrap(paneImpl.editor.peer.getActionMap.get("redo"))

    private def saveText(newTextValue: String)(implicit tx: S#Tx): Option[UndoableEdit] =
      if (!editable) None else Markdown.Var.unapply(markdownH()).map { vr =>
        val newMarkdown = Markdown.newConst[S](newTextValue)
        EditVar.Expr[S, Markdown.Value, Markdown]("Change Source Markdown", vr, newMarkdown)
      }

    private def addEditAndClear(edit: UndoableEdit): Unit = {
      requireEDT()
      undoManager.add(edit)
      // this doesn't work properly
      // component.setDirty(value = false) // do not erase undo history

      // so let's clear the undo history now...
      paneImpl.editor.peer.getDocument.asInstanceOf[SyntaxDocument].clearUndos()
    }

    def save(): Unit = {
      requireEDT()
      val newMarkdown = currentText
      val editOpt = cursor.step { implicit tx =>
        saveText(newMarkdown)
      }
      editOpt.foreach(addEditAndClear)
    }

    def markdown(implicit tx: S#Tx): Markdown[S] = markdownH()

    private def renderAndShow(): Unit = {
      val t = currentText
      cursor.step { implicit tx =>
        val md = markdown
        renderer.setInProgress(md, t /* md.value */)
      }
      tabs.selection.index = 1
    }

    def init(initialText: String, showEditor: Boolean)(implicit tx: S#Tx): this.type = {
      deferTx(guiInit(initialText, showEditor = showEditor))
      this
    }

    private def guiInit(initialText: String, showEditor: Boolean): Unit = {
      paneImpl            = createPane(initialText)
      actionApply         = Action("Apply")(save())
      actionRender        = Action(null   )(renderAndShow())
      actionApply.enabled = false

      lazy val doc = paneImpl.editor.peer.getDocument.asInstanceOf[SyntaxDocument]

      doc.addPropertyChangeListener(SyntaxDocument.CAN_UNDO, new PropertyChangeListener {
        def propertyChange(e: PropertyChangeEvent): Unit =
          dirty = doc.canUndo
      })

      val ksRender  = KeyStrokes.menu1 + Key.Enter
      val ttRender  = s"Render (${Util.keyStrokeText(ksRender)})"

      lazy val ggApply : Button = GUI.toolButton(actionApply , raphael.Shapes.Check       , tooltip = "Save text changes")
      lazy val ggRender: Button = GUI.toolButton(actionRender, raphael.Shapes.RefreshArrow, tooltip = ttRender)

      Util.addGlobalKeyWhenVisible(ggRender, ksRender)

      val bot1: List[Component] = if (bottom.isEmpty) Nil else bottom.iterator.map(_.component).toList
      val bot2 = HGlue :: ggApply :: ggRender :: bot1
      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(bot2: _*)

      val paneEdit = new BorderPanel {
        add(paneImpl.component, BorderPanel.Position.Center)
        add(panelBottom       , BorderPanel.Position.South )
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
        paneImpl.component.requestFocus()
      } else {
        _tabs.selection.index = 1
//        paneEdit.preferredSize = renderer.component.preferredSize
      }
    }
  }
}