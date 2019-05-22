/*
 *  WidgetFrameImpl.scala
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

package de.sciss.mellite.gui.impl.widget

import de.sciss.desktop.UndoManager
import de.sciss.lucre.expr.{BooleanObj, CellView}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.WindowImpl
import de.sciss.mellite.gui.impl.code.CodeFrameBase
import de.sciss.mellite.gui.{CodeView, WidgetEditorFrame, WidgetEditorView, WidgetRenderFrame, WidgetRenderView}
import de.sciss.synth.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}

object WidgetFrameImpl {
  def editor[S <: Sys[S]](obj: Widget[S], bottom: ISeq[View[S]])
                         (implicit tx: S#Tx, universe: Universe[S]): WidgetEditorFrame[S] = {
    implicit val undo: UndoManager = UndoManager()
    val showEditor  = obj.attr.$[BooleanObj](Widget.attrEditMode).forall(_.value)
    val view        = WidgetEditorView(obj, showEditor = showEditor, bottom = bottom)
    val res         = new EditorFrameImpl[S](view).init()
    trackTitle(res, view.renderer)
    res
  }

  def render[S <: Sys[S]](obj: Widget[S])
                         (implicit tx: S#Tx, universe: Universe[S]): WidgetRenderFrame[S] = {
    val view  = WidgetRenderView(obj)
    val res   = new RenderFrameImpl[S](view).init()
    trackTitle(res, view)
    res
  }

  private def setTitle[S <: Sys[S]](win: WindowImpl[S], md: Widget[S])(implicit tx: S#Tx): Unit =
    win.setTitleExpr(Some(CellView.name(md)))

  private def trackTitle[S <: Sys[S]](win: WindowImpl[S], renderer: WidgetRenderView[S])(implicit tx: S#Tx): Unit = {
    setTitle(win, renderer.widget)
    renderer.react { implicit tx => {
      case WidgetRenderView.FollowedLink(_, now) => setTitle(win, now)
    }}
  }

  // ---- frame impl ----

  private final class RenderFrameImpl[S <: Sys[S]](val view: WidgetRenderView[S])
    extends WindowImpl[S] with WidgetRenderFrame[S]

  private final class EditorFrameImpl[S <: Sys[S]](val view: WidgetEditorView[S])
    extends WindowImpl[S] with CodeFrameBase[S] with WidgetEditorFrame[S] {

    protected def codeView: CodeView[S, _] = view.codeView

    override protected def initGUI(): Unit = {
      super.initGUI()
      mkExamplesMenu(Widget.Code.examples)
    }
  }
}