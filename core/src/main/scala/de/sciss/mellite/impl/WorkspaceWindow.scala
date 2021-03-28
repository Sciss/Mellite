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
import de.sciss.lucre.{IntVector, Obj, Txn}
import de.sciss.mellite.{Prefs, ViewState}
import de.sciss.proc.Tag
import de.sciss.synth.UGenSource.Vec

import scala.concurrent.Future
import scala.swing.event.{UIElementMoved, UIElementResized, UIElementShown}
import scala.swing.{Reactions, UIElement}

abstract class WorkspaceWindow[T <: Txn[T]] protected (titleExpr: Option[CellView[T, String]])
  extends WindowImpl[T](titleExpr) {

  def this() = this(None)
  def this(titleExpr: CellView[T, String]) = this(Some(titleExpr))

  @volatile
  private var lastViewState = Set.empty[ViewState]

  private var dirtyBounds = false

  protected def viewObj(implicit tx: T): Obj[T]

  @volatile
  private var stateBounds: Bounds = _

  final def init(viewObj: Obj[T])(implicit tx: T): this.type = {
    stateBounds = (for {
      attr    <- tx.attrMapOption(viewObj)
      tag     <- attr.$[Tag](WindowImpl.StateKey_Base)
      tAttr   <- tx.attrMapOption(tag)
      bounds  <- tAttr.$[IntVector](WindowImpl.StateKey_Bounds)
    } yield {
      bounds.value match {
        case Vec(x, y, width, height) => Bounds(x, y, width, height)
        case _ => null
      }
    }).orNull

    init()
  }

  override protected def packAndPlace: Boolean = stateBounds == null

  private def saveViewState: Boolean = Prefs.viewSaveState.getOrElse(false)

  protected def viewState: Set[ViewState]

  override protected def performClose(): Future[Unit] = {
    if (saveViewState) {
      val state0  = viewState
      val state1  = if (!dirtyBounds) state0 else {
        val entry = ViewState(WindowImpl.StateKey_Bounds, IntVector, stateBounds.toVector)
        state0 + entry
      }
      lastViewState = state1
    }
    super.performClose()
  }

  override def dispose()(implicit tx: T): Unit = {
    if (!wasDisposed && lastViewState.nonEmpty) {
      val attr  = viewObj.attr
      val tag   = attr.$[Tag](WindowImpl.StateKey_Base).getOrElse {
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
    var MIN_BOUNDS_TIME = Long.MaxValue

    def updateBounds(e: UIElement): Unit = {
      val pt = e.bounds
      val b  = Bounds(pt.x, pt.y, pt.width, pt.height)
      if (stateBounds != b) {
        stateBounds = b
        val vis     = System.currentTimeMillis() > MIN_BOUNDS_TIME //  e.visible
        // println(s"updateBounds: $stateBounds - $vis")
        if (vis) dirtyBounds = true
      }
    }

    if (!packAndPlace) {
      val b = stateBounds
      window.component.peer.setBounds(b.x, b.y, b.width, b.height)
    }

    val rp  = window.component
    val r   = new Reactions.Impl
    rp.subscribe(r)
    r += {
      case UIElementMoved   (e) => updateBounds(e)
      case UIElementResized (e) => updateBounds(e)
      case UIElementShown   (_) => MIN_BOUNDS_TIME = System.currentTimeMillis() + 1000  // XXX TODO
    }
  }
}
