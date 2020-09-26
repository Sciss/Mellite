/*
 *  MarkdownRenderViewImpl.scala
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

package de.sciss.mellite.impl.markdown

import de.sciss.desktop
import de.sciss.desktop.{Desktop, KeyStrokes, OptionPane, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.{GUI, MarkdownFrame, MarkdownRenderView, ObjListView}
import de.sciss.mellite.impl.component.{NavigationHistory, ZoomSupport}
import de.sciss.synth.proc
import de.sciss.synth.proc.{Markdown, Universe}
import javax.swing.event.{HyperlinkEvent, HyperlinkListener}
import org.pegdown.{Extensions, PegDownProcessor}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, Component, FlowPanel, ScrollPane, Swing}

object MarkdownRenderViewImpl extends MarkdownRenderView.Companion {
  def install(): Unit =
    MarkdownRenderView.peer = this

  def apply[T <: SSys[T]](init: Markdown[T], bottom: ISeq[View[T]], embedded: Boolean)
                         (implicit tx: T, universe: Universe[T]): MarkdownRenderView[T] =
    new Impl[T](bottom, embedded = embedded).init(init)

  def basic[T <: Txn[T]](init: Markdown[T], bottom: ISeq[View[T]], embedded: Boolean)
                        (implicit tx: T, cursor: Cursor[T]): MarkdownRenderView.Basic[T] =
    new BasicImpl[T](bottom, embedded = embedded).init(init)

  private final class Impl[T <: SSys[T]](bottom: ISeq[View[T]], embedded: Boolean)
                                        (implicit val universe: Universe[T])
    extends Base[T](bottom, embedded) with MarkdownRenderView[T] { impl =>

    protected def mkEditButton(): Option[Component] = {
      if (embedded) None else {
        val actionEdit = Action(null) {
          cursor.step { implicit tx =>
            MarkdownFrame.editor(markdown)
          }
        }
        val ggEdit = GUI.toolButton(actionEdit, raphael.Shapes.Edit)
        Some(ggEdit)
      }
    }

    protected def viewAttr(obj: Obj[T])(implicit tx: T): Option[Window[T]] = {
      val listView = ObjListView(obj)
      if (listView.isViewable) {
        listView.openView(Window.find(impl))
      } else {
        None
      }
    }
  }

  private final class BasicImpl[T <: Txn[T]](bottom: ISeq[View[T]], embedded: Boolean)
                                             (implicit val cursor: Cursor[T])
    extends Base[T](bottom, embedded) {

    protected def mkEditButton(): Option[Component] = None

    protected def viewAttr(obj: Obj[T])(implicit tx: T): Option[Window[T]] = None
  }

  private final case class Percent(value: Int) {
    override def toString: String = s"$value%"

    def fraction: Double = value * 0.01
  }

  private abstract class Base[T <: Txn[T]](bottom: ISeq[View[T]], embedded: Boolean)
    extends MarkdownRenderView.Basic[T]
      with ZoomSupport
      with ComponentHolder[Component]
      with ObservableImpl[T, MarkdownRenderView.Update[T]] { impl =>

    type C = Component

    // ---- abstract ----

    def cursor: Cursor[T]

    protected def mkEditButton(): Option[Component]

    protected def viewAttr(obj: Obj[T])(implicit tx: T): Option[Window[T]]

    // ---- impl ----

    private[this] val mdRef = Ref.make[(Source[T, Markdown[T]], Disposable[T])]()
    private[this] var _editor: HTMLEditorPaneWithZoom = _
    private[this] val nav   = NavigationHistory.empty[T, Source[T, Markdown[T]]]
    private[this] var actionBwd: Action = _
    private[this] var actionFwd: Action = _
    private[this] var obsNav: Disposable[T] = _

    def dispose()(implicit tx: T): Unit = {
      mdRef()._2.dispose()
      obsNav    .dispose()
    }

    def markdown(implicit tx: T): Markdown[T] = mdRef()._1.apply()

    def init(obj: Markdown[T])(implicit tx: T): this.type = {
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

    def setInProgress(md: Markdown[T], value: String)(implicit tx: T): Unit = {
      val obs = md.changed.react { implicit tx => upd =>
        val newText = upd.now
        deferTx(setText(newText))
      }
      val old = mdRef.swap(tx.newHandle(md) -> obs)
      if (old != null) old._2.dispose()

      deferTx(setText(value))
    }

    def markdown_=(md: Markdown[T])(implicit tx: T): Unit =
      setMarkdown(md, reset = true)

    private def setMarkdownFromNav()(implicit tx: T): Unit =
      nav.current.foreach { mdH =>
        val md = mdH()
        setInProgress(md, md.value)
      }

    private def setMarkdown(md: Markdown[T], reset: Boolean)(implicit tx: T): Unit = {
      setInProgress(md, md.value)
      val mdH = tx.newHandle(md)
      if (reset) nav.resetTo(mdH) else nav.push(mdH)
    }

    private def setText(text: String): Unit = {
      requireEDT()
      // Note: unless we have line wrap in the editor,
      // we should not use hard wraps in the rendering.
      // Note: task list items are not correctly rendered
      // with WebLaF (checkboxes are always unselected)
      val mdp       = new PegDownProcessor(Extensions.SMARTYPANTS | /*Extensions.HARDWRAPS |*/ Extensions.TABLES |
        Extensions.FENCED_CODE_BLOCKS /*| Extensions.TASKLISTITEMS*/)
      val html      = mdp.markdownToHtml(text)
      _editor.text  = html
      _editor.peer.setCaretPosition(0)
    }

    protected def setZoomFactor(f: Float): Unit =
      _editor.zoom = f

    private def guiInit(): Unit = {
      _editor = new HTMLEditorPaneWithZoom("") {
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
                    case md: Markdown[T] =>
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

      val paneRender = new ScrollPane(_editor)
      paneRender.peer.putClientProperty("styleId", "undecorated")

      val ggBwd  = GUI.toolButton(actionBwd, raphael.Shapes.Backward)
      val ggFwd  = GUI.toolButton(actionFwd, raphael.Shapes.Forward )

      val ggZoom = initZoomWithComboBox()

      if (!embedded) {
        import KeyStrokes._
        Util.addGlobalKey(ggBwd, alt + Key.Left)
        Util.addGlobalKey(ggFwd, alt + Key.Left)
      }

      val bot1: List[Component] = if (bottom.isEmpty) Nil else bottom.iterator.map(_.component).toList
      val bot2 = mkEditButton().fold(bot1)(_ :: bot1)
      val bot3 = HGlue :: ggZoom :: ggBwd :: ggFwd :: bot2
      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(bot3: _*)

      val pane = new BorderPanel {
        add(paneRender  , BorderPanel.Position.Center)
        add(panelBottom , BorderPanel.Position.South )
      }

      component = pane
    }
  }
}