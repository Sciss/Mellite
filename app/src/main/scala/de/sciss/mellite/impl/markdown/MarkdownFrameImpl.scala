/*
 *  MarkdownFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.markdown

import de.sciss.desktop.{OptionPane, UndoManager}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.{BooleanObj, Cursor, Txn => LTxn}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.{WindowImpl, WorkspaceWindow}
import de.sciss.mellite.{MarkdownEditorView, MarkdownFrame, MarkdownRenderView, Veto}
import de.sciss.processor.Processor.Aborted
import de.sciss.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{Future, Promise}

object MarkdownFrameImpl extends MarkdownFrame.Companion {
  def install(): Unit =
    MarkdownFrame.peer = this

  def editor[T <: Txn[T]](obj: Markdown[T], bottom: ISeq[View[T]])
                        (implicit tx: T, universe: Universe[T]): MarkdownFrame.Editor[T] = {
    implicit val undo: UndoManager = UndoManager()
    val showEditor  = obj.attr.$[BooleanObj](Markdown.attrEditMode).forall(_.value)
    val view        = MarkdownEditorView(obj, showEditor = showEditor, bottom = bottom)
    val res         = new EditorFrameImpl[T](view).init()
    trackTitle(res, view.renderer)
    res
  }

  def render[T <: Txn[T]](obj: Markdown[T])
                         (implicit tx: T, universe: Universe[T]): MarkdownFrame.Render[T] = {
    val view  = MarkdownRenderView(obj)
    val res   = new RenderFrameImpl[T](view).init()
    trackTitle(res, view)
    res
  }

  def basic[T <: LTxn[T]](obj: Markdown[T])
                        (implicit tx: T, cursor: Cursor[T]): MarkdownFrame.Basic[T] = {
    val view  = MarkdownRenderView.basic(obj)
    val res   = new BasicImpl[T](view).init()
    trackTitle(res, view)
    res
  }

  private def setTitle[T <: LTxn[T]](win: WindowImpl[T], md: Markdown[T])(implicit tx: T): Unit =
    win.setTitleExpr(Some(CellView.name(md)))

  private def trackTitle[T <: LTxn[T]](win: WindowImpl[T], renderer: MarkdownRenderView.Basic[T])
                                     (implicit tx: T): Unit = {
    setTitle(win, renderer.markdown)
    renderer.react { implicit tx => {
      case MarkdownRenderView.FollowedLink(_, now) => setTitle(win, now)
    }}
  }

  // ---- frame impl ----

  private final class RenderFrameImpl[T <: Txn[T]](val view: MarkdownRenderView[T])
    extends WorkspaceWindow[T] with MarkdownFrame.Render[T] {
  }
  private final class BasicImpl[T <: LTxn[T]](val view: MarkdownRenderView.Basic[T])
    extends WindowImpl[T] with MarkdownFrame.Basic[T]

  private final class EditorFrameImpl[T <: Txn[T]](val view: MarkdownEditorView[T])
    extends WorkspaceWindow[T] with MarkdownFrame.Editor[T] with Veto[T] {

    override def prepareDisposal()(implicit tx: T): Option[Veto[T]] =
      if (!view.dirty) None else Some(this)


    private[this] def _vetoMessage = "The text has been edited."

    def vetoMessage(implicit tx: T): String = _vetoMessage

    /** Attempts to resolve the veto condition by consulting the user.
      *
      * @return successful future if the situation is resolved, e.g. the user agrees to
      *         proceed with the operation. failed future if the veto is upheld, and
      *         the caller should abort the operation.
      */
    def tryResolveVeto()(implicit tx: T): Future[Unit] = {
      val p = Promise[Unit]()
      deferTx {
        val message = s"${_vetoMessage}\nDo you want to save the changes?"
        val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
          messageType = OptionPane.Message.Warning)
        opt.title = s"Close - $title"
        (opt.show(Some(window)): @unchecked) match {
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