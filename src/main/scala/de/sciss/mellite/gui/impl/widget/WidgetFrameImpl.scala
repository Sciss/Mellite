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

package de.sciss.mellite
package gui.impl.widget

import de.sciss.desktop.{OptionPane, UndoManager}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.WindowImpl
import de.sciss.mellite.gui.{WidgetEditorFrame, WidgetEditorView, WidgetRenderFrame, WidgetRenderView}
import de.sciss.mellite.util.Veto
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{Future, Promise}

object WidgetFrameImpl {
  def editor[S <: Sys[S]](obj: Widget[S], bottom: ISeq[View[S]])
                         (implicit tx: S#Tx, universe: Universe[S]): WidgetEditorFrame[S] = {
    implicit val undo: UndoManager = UndoManager()
    val view  = WidgetEditorView(obj, bottom = bottom)
    val res   = new EditorFrameImpl[S](view).init()
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
    extends WindowImpl[S] with WidgetRenderFrame[S] {

  }

  private final class EditorFrameImpl[S <: Sys[S]](val view: WidgetEditorView[S])
    extends WindowImpl[S] with WidgetEditorFrame[S] with Veto[S#Tx] {

    override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
      if (!view.codeView.dirty) None else Some(this)

    private[this] def _vetoMessage = "The text has been edited."

    def vetoMessage(implicit tx: S#Tx): String = _vetoMessage

    /** Attempts to resolve the veto condition by consulting the user.
      *
      * @return successful future if the situation is resolved, e.g. the user agrees to
      *         proceed with the operation. failed future if the veto is upheld, and
      *         the caller should abort the operation.
      */
    def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] = {
      val p = Promise[Unit]()
      deferTx {
        val message = s"${_vetoMessage}\nDo you want to save the changes?"
        val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
          messageType = OptionPane.Message.Warning)
        opt.title = s"Close - $title"
        opt.show(Some(window)) match {
          case OptionPane.Result.No =>
            p.success(())

          case OptionPane.Result.Yes =>
            val fut = view.codeView.save()
            p.completeWith(fut)
//            p.success(())

          case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
            p.failure(Aborted())
        }
      }
      p.future
    }
  }
}