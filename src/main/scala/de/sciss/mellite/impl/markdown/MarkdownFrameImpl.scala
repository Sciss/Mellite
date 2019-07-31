/*
 *  MarkdownFrameImpl.scala
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

import de.sciss.desktop.{OptionPane, UndoManager}
import de.sciss.lucre.expr.{BooleanObj, CellView}
import de.sciss.lucre.stm
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{MarkdownEditorView, MarkdownFrame, MarkdownRenderView, Veto}
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{Future, Promise}

object MarkdownFrameImpl extends MarkdownFrame.Companion {
  def install(): Unit =
    MarkdownFrame.peer = this

  def editor[S <: Sys[S]](obj: Markdown[S], bottom: ISeq[View[S]])
                        (implicit tx: S#Tx, universe: Universe[S]): MarkdownFrame.Editor[S] = {
    implicit val undo: UndoManager = UndoManager()
    val showEditor  = obj.attr.$[BooleanObj](Markdown.attrEditMode).forall(_.value)
    val view        = MarkdownEditorView(obj, showEditor = showEditor, bottom = bottom)
    val res         = new EditorFrameImpl[S](view).init()
    trackTitle(res, view.renderer)
    res
  }

  def render[S <: Sys[S]](obj: Markdown[S])
                         (implicit tx: S#Tx, universe: Universe[S]): MarkdownFrame.Render[S] = {
    val view  = MarkdownRenderView(obj)
    val res   = new RenderFrameImpl[S](view).init()
    trackTitle(res, view)
    res
  }

  def basic[S <: stm.Sys[S]](obj: Markdown[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): MarkdownFrame.Basic[S] = {
    val view  = MarkdownRenderView.basic(obj)
    val res   = new BasicImpl[S](view).init()
    trackTitle(res, view)
    res
  }

  private def setTitle[S <: stm.Sys[S]](win: WindowImpl[S], md: Markdown[S])(implicit tx: S#Tx): Unit =
    win.setTitleExpr(Some(CellView.name(md)))

  private def trackTitle[S <: stm.Sys[S]](win: WindowImpl[S], renderer: MarkdownRenderView.Basic[S])
                                     (implicit tx: S#Tx): Unit = {
    setTitle(win, renderer.markdown)
    renderer.react { implicit tx => {
      case MarkdownRenderView.FollowedLink(_, now) => setTitle(win, now)
    }}
  }

  // ---- frame impl ----

  private final class RenderFrameImpl[S <: Sys[S]](val view: MarkdownRenderView[S])
    extends WindowImpl[S] with MarkdownFrame.Render[S] {
  }
  private final class BasicImpl[S <: stm.Sys[S]](val view: MarkdownRenderView.Basic[S])
    extends WindowImpl[S] with MarkdownFrame.Basic[S]

  private final class EditorFrameImpl[S <: Sys[S]](val view: MarkdownEditorView[S])
    extends WindowImpl[S] with MarkdownFrame.Editor[S] with Veto[S#Tx] {

    override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
      if (!view.dirty) None else Some(this)


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
            /* val fut = */ view.save()
            p.success(())

          case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
            p.failure(Aborted())
        }
      }
      p.future
    }
  }
}