/*
 *  MarkdownRenderViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui.impl.markdown

import javax.swing.event.{HyperlinkEvent, HyperlinkListener}
import de.sciss.desktop
import de.sciss.desktop.{Desktop, KeyStrokes, OptionPane, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window, deferTx, requireEDT}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.{GUI, ListObjView, MarkdownEditorFrame, MarkdownRenderView}
import de.sciss.mellite.gui.impl.component.NavigationHistory
import de.sciss.swingplus.EditorPane
import de.sciss.synth.proc
import de.sciss.synth.proc.{Markdown, Workspace}
import org.pegdown.PegDownProcessor

import scala.collection.breakOut
import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, Component, FlowPanel, ScrollPane, Swing}

object MarkdownRenderViewImpl {
  def apply[S <: SSys[S]](init: Markdown[S], bottom: ISeq[View[S]], embedded: Boolean)
                         (implicit tx: S#Tx, workspace: Workspace[S],
                                             cursor: stm.Cursor[S]): MarkdownRenderView[S] =
    new Impl[S](bottom, embedded = embedded).init(init)

  def basic[S <: Sys[S]](init: Markdown[S], bottom: ISeq[View[S]], embedded: Boolean)
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): MarkdownRenderView.Basic[S] =
    new BasicImpl[S](bottom, embedded = embedded).init(init)

  private final class Impl[S <: SSys[S]](bottom: ISeq[View[S]], embedded: Boolean)
                                        (implicit val workspace: Workspace[S], cursor: stm.Cursor[S])
    extends Base[S](bottom, embedded) with MarkdownRenderView[S] { impl =>

    protected def mkEditButton(): Option[Component] = {
      if (embedded) None else {
        val actionEdit = Action(null) {
          cursor.step { implicit tx =>
            MarkdownEditorFrame(markdown)
          }
        }
        val ggEdit = GUI.toolButton(actionEdit, raphael.Shapes.Edit)
        Some(ggEdit)
      }
    }

    protected def viewAttr(obj: Obj[S])(implicit tx: S#Tx): Option[Window[S]] = {
      val listView = ListObjView(obj)
      if (listView.isViewable) {
        import impl.{cursor => txCursor}
        listView.openView(Window.find(impl))
      } else {
        None
      }
    }
  }

  private final class BasicImpl[S <: Sys[S]](bottom: ISeq[View[S]], embedded: Boolean)
                                             (implicit cursor: stm.Cursor[S])
    extends Base[S](bottom, embedded) {

    protected def mkEditButton(): Option[Component] = None

    protected def viewAttr(obj: Obj[S])(implicit tx: S#Tx): Option[Window[S]] = None
  }

  private abstract class Base[S <: Sys[S]](bottom: ISeq[View[S]], embedded: Boolean)
                                           (implicit val cursor: stm.Cursor[S])
    extends MarkdownRenderView.Basic[S]
      with ComponentHolder[Component]
      with ObservableImpl[S, MarkdownRenderView.Update[S]] { impl =>

    type C = Component

    // ---- abstract ----

    protected def mkEditButton(): Option[Component]

    protected def viewAttr(obj: Obj[S])(implicit tx: S#Tx): Option[Window[S]]

    // ---- impl ----

    private[this] val mdRef = Ref.make[(stm.Source[S#Tx, Markdown[S]], Disposable[S#Tx])]
    private[this] var _editor: EditorPane = _
    private[this] val nav   = NavigationHistory.empty[S, stm.Source[S#Tx, Markdown[S]]]
    private[this] var actionBwd: Action = _
    private[this] var actionFwd: Action = _
    private[this] var obsNav: Disposable[S#Tx] = _

    def dispose()(implicit tx: S#Tx): Unit = {
      mdRef()._2.dispose()
      obsNav    .dispose()
    }

    def markdown(implicit tx: S#Tx): Markdown[S] = mdRef()._1.apply()

    def init(obj: Markdown[S])(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      markdown = obj
      obsNav = nav.react { implicit tx => upd =>
        deferTx {
          actionBwd.enabled = upd.canGoBack
          actionFwd.enabled = upd.canGoForward
        }
      }
      this
    }

    def setInProgress(md: Markdown[S], value: String)(implicit tx: S#Tx): Unit = {
      val obs = md.changed.react { implicit tx => upd =>
        val newText = upd.now
        deferTx(setText(newText))
      }
      val old = mdRef.swap(tx.newHandle(md) -> obs)
      if (old != null) old._2.dispose()

      deferTx(setText(value))
    }

    def markdown_=(md: Markdown[S])(implicit tx: S#Tx): Unit =
      setMarkdown(md, reset = true)

    private def setMarkdownFromNav()(implicit tx: S#Tx): Unit =
      nav.current.foreach { mdH =>
        val md = mdH()
        setInProgress(md, md.value)
      }

    private def setMarkdown(md: Markdown[S], reset: Boolean)(implicit tx: S#Tx): Unit = {
      setInProgress(md, md.value)
      val mdH = tx.newHandle(md)
      if (reset) nav.resetTo(mdH) else nav.push(mdH)
    }

    private def setText(text: String): Unit = {
      requireEDT()
      val mdp       = new PegDownProcessor
      val html      = mdp.markdownToHtml(text)
      _editor.text  = html
      _editor.peer.setCaretPosition(0)
    }

    private def guiInit(): Unit = {
      _editor = new EditorPane("text/html", "") {
        editable      = false
        border        = Swing.EmptyBorder(8)
        preferredSize = (500, 500)

        peer.addHyperlinkListener(new HyperlinkListener {
          def hyperlinkUpdate(e: HyperlinkEvent): Unit = {
            if (e.getEventType == HyperlinkEvent.EventType.ACTIVATED) {
              // println(s"description: ${e.getDescription}")
              // println(s"source elem: ${e.getSourceElement}")
              // println(s"url        : ${e.getURL}")
              // val link = e.getDescription
              // val ident = if (link.startsWith("ugen.")) link.substring(5) else link
              // lookUpHelp(ident)

              val url = e.getURL
              if (url != null) {
                Desktop.browseURI(url.toURI)
              } else {
                val key = e.getDescription
                val either: Either[String, Unit] = impl.cursor.step { implicit tx =>
                  val obj = markdown
                  obj.attr.get(key).fold[Either[String, Unit]] {
                    import proc.Implicits._
                    Left(s"Attribute '$key' not found in Markdown object '${obj.name}'")
                  } {
                    case md: Markdown[S] =>
                      nav.push(tx.newHandle(md))
                      setMarkdownFromNav()
                      fire(MarkdownRenderView.FollowedLink(impl, md))
                      Right(())

                    case other =>
                      val opt = viewAttr(other)
                      opt match {
                        case Some(_) => Right(())
                        case None =>
                          import proc.Implicits._
                          Left(s"Object '${other.name}' in attribute '$key' is not viewable")
                      }
                  }
                }
                either.left.foreach { message =>
                  val opt = OptionPane.message(message, OptionPane.Message.Error)
                  opt.show(desktop.Window.find(impl.component), "Markdown Link")
                }
              }
            }
          }
        })
      }

      actionBwd = Action(null) {
        cursor.step { implicit tx =>
          if (nav.canGoBack) {
            nav.backward()
            setMarkdownFromNav()
          }
        }
      }
      actionBwd.enabled = false

      actionFwd = Action(null) {
        cursor.step { implicit tx =>
          if (nav.canGoForward) {
            nav.forward()
            setMarkdownFromNav()
          }
        }
      }
      actionFwd.enabled = false

      val ggBwd = GUI.toolButton(actionBwd, raphael.Shapes.Backward)
      val ggFwd = GUI.toolButton(actionFwd, raphael.Shapes.Forward )

      if (!embedded) {
        import KeyStrokes._
        Util.addGlobalKey(ggBwd, alt + Key.Left)
        Util.addGlobalKey(ggFwd, alt + Key.Left)
      }

      val bot1: List[Component] = if (bottom.isEmpty) Nil else bottom.map(_.component)(breakOut)
      val bot2 = mkEditButton().fold(bot1)(_ :: bot1)
      val bot3 = HGlue :: ggBwd :: ggFwd :: bot2
      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(bot3: _*)

      val paneRender = new ScrollPane(_editor)
      paneRender.peer.putClientProperty("styleId", "undecorated")

      val pane = new BorderPanel {
        add(paneRender  , BorderPanel.Position.Center)
        add(panelBottom , BorderPanel.Position.South )
      }

      component = pane
    }
  }
}