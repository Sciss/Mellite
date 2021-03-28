/*
 *  WindowImpl.scala
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

import de.sciss.desktop
import de.sciss.desktop.WindowHandler
import de.sciss.file._
import de.sciss.lucre.Txn.peer
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.{Disposable, Txn}
import de.sciss.mellite.{Application, CanBounce, DependentMayVeto, DocumentViewHandler, UniverseView, Veto, WindowPlacement}

import scala.concurrent.stm.Ref
import scala.concurrent.{ExecutionContext, Future}
import scala.swing.Action
import scala.util.Success

object WindowImpl {
  final val StateKey_Bounds = "window-bounds"
  final val StateKey_Base   = "view"

  private final class Peer[T <: Txn[T]](view: View[T], impl: WindowImpl[T],
                                        undoRedoActions: Option[(Action, Action)],
                                        override val style: desktop.Window.Style,
                                        undecorated: Boolean, callPack: Boolean)
    extends desktop.impl.WindowImpl {

    if (undecorated) makeUndecorated()

    def handler: WindowHandler = Application.windowHandler

//    bindMenu("actions.window-shot", new ActionWindowShot(this))

    // addAction("window-shot", new ActionWindowShot(this))

    view match {
      case fv: View.File => file = Some(fv.file)
      case _ =>
    }
    file.map(_.base).foreach(title_=)

    contents  = view.component
    closeOperation = desktop.Window.CloseIgnore
    reactions += {
      case desktop.Window.Closing  (_) =>
        impl.handleClose()
      case desktop.Window.Activated(_) =>
        view match {
          case uv: UniverseView[T] =>
            DocumentViewHandler.instance.activeDocument = Some(uv.universe)
          case _ =>
        }
    }

    bindMenu("file.close", Action(null)(impl.handleClose()))

    view match {
      case c: CanBounce => bindMenu("file.bounce", c.actionBounce)
      case _ =>
    }

    undoRedoActions.foreach { case (undo, redo) =>
      bindMenus(
        "edit.undo" -> undo,
        "edit.redo" -> redo
      )
    }

    if (callPack) pack()
  }
}

abstract class WindowImpl[T <: Txn[T]] protected (titleExpr: Option[CellView[T, String]])
  extends Window[T] with WindowHolder[desktop.Window] with DependentMayVeto[T] {
  impl =>

  def this() = this(None)
  def this(titleExpr: CellView[T, String]) = this(Some(titleExpr))

  protected def style: desktop.Window.Style = desktop.Window.Regular

  private[this] var windowImpl: WindowImpl.Peer[T] = _
  private[this] val titleObserver = Ref(Disposable.empty[T])

  final def title        : String        = windowImpl.title
  final def title_=(value: String): Unit = windowImpl.title = value

//  final def dirty        : Boolean        = windowImpl.dirty
//  final def dirty_=(value: Boolean): Unit = windowImpl.dirty = value

//  final def resizable        : Boolean        = windowImpl.resizable
//  final def resizable_=(value: Boolean): Unit = windowImpl.resizable = value

  protected def undecorated : Boolean = false
  protected def packAndPlace: Boolean = true

  final def windowFile        : Option[File]        = windowImpl.file
  final def windowFile_=(value: Option[File]): Unit = windowImpl.file = value

  final protected def bindMenus(entries: (String, Action)*): Unit = windowImpl.bindMenus(entries: _*)

  def setTitleExpr(exprOpt: Option[CellView[T, String]])(implicit tx: T): Unit = {
    titleObserver.swap(Disposable.empty).dispose()
    exprOpt.foreach { ex =>
      def update(s: String)(implicit tx: T): Unit = deferTx { title = s }

      val obs = ex.react { implicit tx => now => update(now) }
      titleObserver() = obs
      update(ex())
    }
  }

  final def init()(implicit tx: T): this.type = {
    view match {
      case vu: UniverseView[T] =>
        vu.universe.workspace.addDependent(impl)

      case _ =>
    }

    deferTx(initGUI0())
    setTitleExpr(titleExpr)
    this
  }

  private def initGUI0(): Unit = {
    val pp      = packAndPlace
    val f       = new WindowImpl.Peer(view, impl, undoRedoActions, style, undecorated = undecorated, callPack = pp)
    window      = f
    windowImpl  = f
    Window.attach(f, this)
    if (pp) {
      val p = placement
      desktop.Util.placeWindow(f, p.horizontal, p.vertical, p.padding)
    }

    initGUI()
    f.front()
  }

  protected def initGUI(): Unit = ()

  /** Subclasses may override this. The tuple is (horizontal, vertical, padding) position.
    * By default it centers the window, i.e. `(0.5f, 0.5f, 20)`.
    */
  protected def placement: WindowPlacement = WindowPlacement.default

  /** Subclasses may override this. By default this always returns `None`.
    */
  def prepareDisposal()(implicit tx: T): Option[Veto[T]] = None

  /** Subclasses may override this. */
  protected def undoRedoActions: Option[(Action, Action)] =
    view match {
      case ev: View.Editable[T] =>
        val mgr = ev.undoManager
        Some(mgr.undoAction -> mgr.redoAction)
      case _ => None
    }

  private[this] val _wasDisposed = Ref(false)

  // called on the EDT
  protected def performClose(): Future[Unit] =
    view match {
      case cv: View.Cursor[T] =>
        import cv.cursor

        def complete()(implicit tx: T): Unit = {
          deferTx(windowImpl.visible = false)
          dispose()
        }

        def succeed()(implicit tx: T): Future[Unit] = {
          complete()
          Future.successful(())
        }

        cursor.step { implicit tx =>
          val vetoOpt = prepareDisposal()
          vetoOpt.fold[Future[Unit]] {
            succeed()
          } { veto =>
            val futVeto = veto.tryResolveVeto()
            futVeto.value match {
              case Some(Success(())) =>
                succeed()
              case _ =>
                import ExecutionContext.Implicits.global
                futVeto.map { _ =>
                  cursor.step { implicit tx =>
                    complete()
                  }
                }
            }
          }
        }

      case _ =>
        throw new IllegalArgumentException("Cannot close a window whose view has no cursor")
    }

  final def handleClose(): Unit = {
    requireEDT()
    if (!_wasDisposed.single.get) {
      val fut = performClose()
      import ExecutionContext.Implicits.global
      fut.foreach { _ =>
        _wasDisposed.single.set(true)
      }
    }
  }

  def pack(): Unit = windowImpl.pack()

  protected final def wasDisposed(implicit tx: T): Boolean = _wasDisposed.get(tx.peer)

  def dispose()(implicit tx: T): Unit = {
    titleObserver().dispose()

    view match {
      case vu: UniverseView[T] => vu.universe.workspace.removeDependent(this)
      case _ =>
    }

    view.dispose()

    deferTx {
      window.dispose()
    }

    _wasDisposed() = true
  }
}