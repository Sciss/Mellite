/*
 *  WidgetRenderViewImpl.scala
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
package gui.impl.widget

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
import de.sciss.synth.proc.Widget.{Graph, GraphChange}
import de.sciss.synth.proc.{Widget, Workspace}

import scala.collection.breakOut
import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, Component, FlowPanel}

object WidgetRenderViewImpl {
  def apply[S <: SSys[S]](init: Widget[S], bottom: ISeq[View[S]], embedded: Boolean)
                         (implicit tx: S#Tx, workspace: Workspace[S],
                          cursor: stm.Cursor[S]): WidgetRenderView[S] =
    new Impl[S](bottom, embedded = embedded).init(init)

  private final class Impl[S <: SSys[S]](bottom: ISeq[View[S]], embedded: Boolean)
                                        (implicit val workspace: Workspace[S], val cursor: stm.Cursor[S])
    extends WidgetRenderView[S]
      with ComponentHolder[Component]
      with ObservableImpl[S, WidgetRenderView.Update[S]] { impl =>

    type C = Component

    private[this] val widgetRef = Ref.make[(stm.Source[S#Tx, Widget[S]], Disposable[S#Tx])]
    private[this] val graphRef  = Ref.make[Widget.Graph]
    private[this] val viewRef   = Ref(Option.empty[Disposable[S#Tx]])

    //    private[this] var paneRender: Component   = _
    private[this] var paneBorder: BorderPanel1 = _


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

    private class BorderPanel1 extends BorderPanel {
      override def add(c: Component, l: BorderPanel.Position.Value): Unit =
        super.add(c, l)
    }

    def setGraph(g: Graph)(implicit tx: S#Tx): Unit = {
      val old = graphRef.swap(g)
      if (g != old) {
        val v = g.expand[S]
        deferTx {
          paneBorder.add(v.component, BorderPanel.Position.Center)
          paneBorder.revalidate()
        }
        viewRef.swap(Some(v)).foreach(_.dispose())
      }
    }

    private def guiInit(): Unit = {
      val bot1: List[Component] = if (bottom.isEmpty) Nil else bottom.map(_.component)(breakOut)
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

//      paneRender = new ScrollPane // (_editor)
//      paneRender.peer.putClientProperty("styleId", "undecorated")

      paneBorder = new BorderPanel1 {
//        add(paneRender  , BorderPanel.Position.Center)
        add(panelBottom , BorderPanel.Position.South )
      }

      component = paneBorder
    }
  }
}