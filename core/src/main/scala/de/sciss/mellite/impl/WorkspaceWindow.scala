/*
 *  WorkspaceWindow.scala
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

package de.sciss.mellite.impl

import de.sciss.lucre.expr.CellView
import de.sciss.lucre.{IntVector, Txn}
import de.sciss.mellite.{Prefs, UniverseObjView, ViewState}
import de.sciss.proc.Tag
import de.sciss.synth.UGenSource.Vec

import java.awt.event.{ComponentEvent, ComponentListener}
import scala.concurrent.Future

abstract class WorkspaceWindow[T <: Txn[T]] protected (titleExpr: Option[CellView[T, String]])
  extends WindowImpl[T](titleExpr) {

  def this() = this(None)
  def this(titleExpr: CellView[T, String]) = this(Some(titleExpr))

  @volatile
  private var lastViewState = Set.empty[ViewState]

  private var dirtyBounds = false

  override def view: UniverseObjView[T]

  @volatile
  private var stateBounds: Bounds = _

  final override def init()(implicit tx: T): this.type = {
    stateBounds = (for {
      attr    <- tx.attrMapOption(view.obj)
      tag     <- attr.$[Tag](WindowImpl.StateKey_Base)
      tAttr   <- tx.attrMapOption(tag)
      bounds  <- tAttr.$[IntVector](WindowImpl.StateKey_Bounds)
    } yield {
      bounds.value match {
        case Vec(x, y, width, height) => Bounds(x, y, width, height)
        case _ => null
      }
    }).orNull

    super.init()
  }

  override protected def packAndPlace: Boolean = stateBounds == null

  final protected def viewState: Set[ViewState] = view.viewState

  protected final def saveViewState(): Unit = {
    val b = Prefs.viewSaveState.getOrElse(false)
    if (b) {
      val state0  = viewState
      val state1  = if (!dirtyBounds) state0 else {
        val entry = ViewState(WindowImpl.StateKey_Bounds, IntVector, stateBounds.toVector)
        state0 + entry
      }
      lastViewState = state1
    }
  }

  override protected def performClose(): Future[Unit] = {
    saveViewState()
    super.performClose()
  }

  override def dispose()(implicit tx: T): Unit = {
    if (!wasDisposed && lastViewState.nonEmpty) {
      val viewObj = view.obj
      val attr    = viewObj.attr
      val tag     = attr.$[Tag](WindowImpl.StateKey_Base).getOrElse {
        val t = Tag[T]()
        attr.put(WindowImpl.StateKey_Base, t)
        t
      }
      val tAttr = tag.attr

      lastViewState.foreach(_.set(tAttr))
    }
    super.dispose()
  }

  override protected def initGUI(): Unit = {
    if (!packAndPlace) {
      val b = stateBounds
      val j = window.component.peer
      if (resizable) {
        // pack()
        j.setBounds(b.x, b.y, b.width, b.height)
      } else {
        pack()
        j.setLocation(b.x, b.y)
      }
    }

    val rp = window.component
    rp.peer.addComponentListener(new ComponentListener {
      private var minBoundTime = Long.MaxValue

      private def updateBounds(e: ComponentEvent): Unit = {
        val r = e.getComponent.getBounds
        val b = Bounds(r.x, r.y, r.width, r.height)
        if (stateBounds != b) {
          stateBounds = b
          val ok = System.currentTimeMillis() > minBoundTime
          // println(s"updateBounds: $stateBounds - $ok")
          if (ok) dirtyBounds = true
        }
      }

      override def componentResized (e: ComponentEvent): Unit = updateBounds(e)
      override def componentMoved   (e: ComponentEvent): Unit = updateBounds(e)
      override def componentShown   (e: ComponentEvent): Unit = {
        minBoundTime = System.currentTimeMillis() + 500  // XXX TODO
      }

      override def componentHidden(e: ComponentEvent): Unit = ()
    })
  }
}
