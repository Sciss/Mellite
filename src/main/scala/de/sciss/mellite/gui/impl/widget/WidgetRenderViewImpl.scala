/*
 *  WidgetRenderViewImpl.scala
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

import de.sciss.desktop.{KeyStrokes, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Disposable
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.{GUI, WidgetEditorFrame, WidgetRenderView}
import de.sciss.model.Change
import de.sciss.numbers
import de.sciss.synth.proc.UGenGraphBuilder.MissingIn
import de.sciss.synth.proc.Widget.{Graph, GraphChange}
import de.sciss.synth.proc.{Universe, Widget}
import javax.swing.JComponent

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, Component, FlowPanel, Font, Swing}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object WidgetRenderViewImpl {
  def apply[S <: SSys[S]](init: Widget[S], bottom: ISeq[View[S]], embedded: Boolean)
                         (implicit tx: S#Tx, universe: Universe[S]): WidgetRenderView[S] =
    new Impl[S](bottom, embedded = embedded).init(init)

  private final class Impl[S <: SSys[S]](bottom: ISeq[View[S]], embedded: Boolean)
                                        (implicit val universe: Universe[S])
    extends WidgetRenderView[S]
      with ComponentHolder[Component]
      with ObservableImpl[S, WidgetRenderView.Update[S]] { impl =>

    type C = Component

    private[this] val widgetRef   = Ref.make[(stm.Source[S#Tx, Widget[S]], Disposable[S#Tx])]
    private[this] val graphRef    = Ref.make[Widget.Graph]
    private[this] val viewRef     = Ref(Option.empty[Disposable[S#Tx]])
    private[this] var zoomFactor  = 1.0f

    //    private[this] var paneRender: Component   = _
    private[this] var paneBorder: BorderPanelWithAdd = _


    def dispose()(implicit tx: S#Tx): Unit = {
      widgetRef()._2.dispose()
      viewRef.swap(None).foreach(_.dispose())
    }

    def widget(implicit tx: S#Tx): Widget[S] = widgetRef()._1.apply()

    def init(obj: Widget[S])(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      widget = obj
      this
    }

    def widget_=(w: Widget[S])(implicit tx: S#Tx): Unit = {
      val obs = w.changed.react { implicit tx => upd =>
        upd.changes.foreach {
          case GraphChange(Change(_, now)) =>
            setGraph(now)
          case _ =>
        }
      }
      val old = widgetRef.swap(tx.newHandle(w) -> obs)
      if (old != null) old._2.dispose()

      val g = w.graph.value
      setGraph(g)

//      val wH = tx.newHandle(w)
    }

    private class BorderPanelWithAdd extends BorderPanel {
      // make that public
      override def add(c: Component, l: BorderPanel.Position.Value): Unit =
        super.add(c, l)
    }

    private def setZoom(top: JComponent /*, done: mutable.Set[JComponent] = mutable.Set.empty*/): Unit =
      /*if (done.add(top))*/ {
        top.getFont match {
          case f: Font =>
            val fN = f.deriveFont(f.getSize2D * zoomFactor)
            top.setFont(fN)
          case _ =>
        }

        top.getComponents.foreach {
          case c: JComponent => setZoom(c)
          case _ =>
        }
      }

    def setGraph(g: Graph)(implicit tx: S#Tx): Unit = setGraphForce(g, force = false)

    private def setGraphForce(g: Graph, force: Boolean)(implicit tx: S#Tx): Unit = {
      val old = graphRef.swap(g)
      if (force || g != old) {
        // N.B. we have to use `try` instead of `Try` because
        // `MissingIn` is a `ControlThrowable` which would not be caught.
        val vTry = try {
          import universe.workspace
          val res = g.expand[S](self = Some(widget))
          Success(res)
        } catch {
          case ex @ MissingIn(_)  => Failure(ex)
          case NonFatal(ex)       => Failure(ex)
        }
        deferTx {
          val comp = vTry match {
            case Success(v)   => v.component
            case Failure(ex)  =>
              ex.printStackTrace()
              Swing.HGlue
          }
          if (zoomFactor != 1f) setZoom(comp.peer)
          paneBorder.add(comp, BorderPanel.Position.Center)
          paneBorder.revalidate()
        }
        viewRef.swap(vTry.toOption).foreach(_.dispose())
      }
    }

    private def guiInit(): Unit = {
      val bot1: List[Component] = if (bottom.isEmpty) Nil else bottom.iterator.map(_.component).toList
      val bot2 = if (embedded) bot1 else {
        val actionEdit = Action(null) {
          cursor.step { implicit tx =>
            WidgetEditorFrame(widget)
          }
        }
        val ggEdit = GUI.toolButton(actionEdit, raphael.Shapes.Edit)
        ggEdit :: bot1
      }
      val bot3 = HGlue :: bot2
      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(bot3: _*)

      val zoomItems = Vector(25, 50, 75, 100, 125, 150, 200, 250, 300, 350, 400)
      var zoomIdx   = zoomItems.indexOf(100)

      def zoom(dir: Int): Unit = {
        import numbers.Implicits._
        val newIdx = (zoomIdx + dir).clip(0, zoomItems.size - 1)
        if (zoomIdx != newIdx) {
          zoomIdx     = newIdx
          zoomFactor  = zoomItems(newIdx) * 0.01f
          cursor.step { implicit tx =>
            setGraphForce(graphRef(), force = true)
          }
        }
      }

      def zoomIn  (): Unit = zoom(+1)
      def zoomOut (): Unit = zoom(-1)

      import KeyStrokes._
      Util.addGlobalAction(panelBottom, "dec", ctrl + Key.Minus          )(zoomOut ())
      Util.addGlobalAction(panelBottom, "inc", ctrl + Key.Plus           )(zoomIn  ())
      Util.addGlobalAction(panelBottom, "inc", shift + ctrl + Key.Equals )(zoomIn  ())
      // could add ctrl + Key.K0 to reset?

      //      paneRender = new ScrollPane // (_editor)
//      paneRender.peer.putClientProperty("styleId", "undecorated")

      paneBorder = new BorderPanelWithAdd {
//        add(paneRender  , BorderPanel.Position.Center)
        add(panelBottom , BorderPanel.Position.South )
      }

      component = paneBorder
    }
  }
}