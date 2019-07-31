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

package de.sciss.mellite.gui.impl.widget

import de.sciss.icons.raphael
import de.sciss.lucre.expr.Context
import de.sciss.lucre.stm
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Disposable, UndoManager}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.GUI
import de.sciss.mellite.gui.impl.component.ZoomSupport
import de.sciss.mellite.gui.{WidgetEditorFrame, WidgetRenderView}
import de.sciss.model.Change
import de.sciss.synth.proc.UGenGraphBuilder.MissingIn
import de.sciss.synth.proc.Widget.{Graph, GraphChange}
import de.sciss.synth.proc.{ExprContext, Universe, Widget}
import javax.swing.JComponent

import scala.collection.immutable.{Seq => ISeq}
import scala.collection.mutable
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, Component, FlowPanel, Font, Swing}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object WidgetRenderViewImpl {
  def apply[S <: SSys[S]](init: Widget[S], bottom: ISeq[View[S]], embedded: Boolean)
                         (implicit tx: S#Tx, universe: Universe[S],
                          undoManager: UndoManager[S]): WidgetRenderView[S] =
    new Impl[S](bottom, embedded = embedded).init(init)

  private final class Impl[S <: SSys[S]](bottom: ISeq[View[S]], embedded: Boolean)
                                        (implicit val universe: Universe[S], val undoManager: UndoManager[S])
    extends WidgetRenderView[S]
      with ComponentHolder[Component]
      /*with ObservableImpl[S, WidgetRenderView.Update[S]]*/ with ZoomSupport { impl =>

    type C = Component

    private[this] val widgetRef   = Ref.make[(stm.Source[S#Tx, Widget[S]], Disposable[S#Tx])]
    private[this] val graphRef    = Ref.make[Widget.Graph]
    private[this] val viewRef     = Ref(Option.empty[(View[S], Disposable[S#Tx])])
//    private[this] val viewInit    = Ref(-2) // -2 is special code for "no previous view"

    //    private[this] var paneRender: Component   = _
    private[this] var paneBorder: BorderPanelWithAdd = _


    def dispose()(implicit tx: S#Tx): Unit = {
      widgetRef()._2.dispose()
//      universe.scheduler.cancel(viewInit.swap(-1))
      viewRef.swap(None).foreach { tup =>
        tup._1.dispose()
        tup._2.dispose()
      }
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

    private[this] val fontSizeMap = mutable.Map.empty[JComponent, Float]

    private def setZoom(top: JComponent, factor: Float): Unit =
      /*if (done.add(top))*/ {
        top.getFont match {
          case f: Font =>
            val sz0 = fontSizeMap.getOrElseUpdate(top, f.getSize2D)
            val fN  = f.deriveFont(sz0 * factor)
            top.setFont(fN)
          case _ =>
        }

        top.getComponents.foreach {
          case c: JComponent => setZoom(c, factor)
          case _ =>
        }
      }

    def setGraph(g: Graph)(implicit tx: S#Tx): Unit = {
      val old = graphRef.swap(g)
      if (g != old) {
//        val sch = universe.scheduler
//        val oldToken  = viewInit()
//        val hadOld    = oldToken != -2
//        if (hadOld) sch.cancel(oldToken) // cancel previous `initControls`

        // N.B. we have to use `try` instead of `Try` because
        // `MissingIn` is a `ControlThrowable` which would not be caught.
        val vTry = try {
          implicit val ctx: Context[S] = ExprContext(Some(tx.newHandle(widget)))
          val res   = g.expand[S]

//          if (hadOld) {
//            // we give it a delay in order to work-around problems
//            // we reallocating IO resources such as OSC sockets... Not elegant, but...
//            val t     = sch.time + (4.0 * TimeRef.SampleRate).toLong
//            println("DELAY")
//            val token = sch.schedule(t) { implicit tx =>
//              println("INIT")
////              res.initControl()
//            }
//            viewInit() = token
//          } else {
//            println("INIT DIRECT")
////            res.initControl()
//            viewInit() = -1 // not scheduled, but now have view
//          }
          Success((res, ctx))

        } catch {
          case ex @ MissingIn(_)  => Failure(ex)
          case NonFatal(ex)       => Failure(ex)
        }
        deferTx {
          val comp = vTry match {
            case Success((v, _))  => v.component
            case Failure(ex)      =>
              ex.printStackTrace()
              Swing.HGlue
          }
          fontSizeMap.clear()
          if (zoomFactor != 1f) setZoom(comp.peer, zoomFactor)
          paneBorder.add(comp, BorderPanel.Position.Center)
          paneBorder.revalidate()
        }
        viewRef.swap(vTry.toOption).foreach { tupOld =>
          tupOld._1.dispose()
          tupOld._2.dispose()
        }
        // we call `initControl` only after the disposal
        // of the old view, so we can re-use resources (such as OSC sockets)
        vTry.foreach { case (vNew, _) =>
//          println("INIT 1")
          vNew.initControl()
        }
      }
    }

    protected def setZoomFactor(f: Float): Unit = {
      viewRef.single.get.foreach { case (view, _) =>
        setZoom(view.component.peer, f)
        paneBorder.revalidate()
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

      initZoom(panelBottom)

      paneBorder = new BorderPanelWithAdd {
//        add(paneRender  , BorderPanel.Position.Center)
        add(panelBottom , BorderPanel.Position.South )
      }

      component = paneBorder
    }
  }
}