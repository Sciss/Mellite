/*
 *  WindowImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl

import de.sciss.desktop
import de.sciss.desktop.WindowHandler
import de.sciss.file._
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.swing.{CellView, View, Window, deferTx, requireEDT}
import de.sciss.mellite.util.Veto

import scala.concurrent.Future
import scala.concurrent.stm.Ref
import scala.swing.Action
import scala.util.Success

object WindowImpl {
  private final class Peer[S <: Sys[S]](view: View[S], impl: WindowImpl[S],
                                        undoRedoActions: Option[(Action, Action)],
                                        override val style: desktop.Window.Style,
                                        undecorated: Boolean)
    extends desktop.impl.WindowImpl {

    if (undecorated) makeUndecorated()

    def handler: WindowHandler = Application.windowHandler

    bindMenu("actions.window-shot", new ActionWindowShot(this))

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
          case wv: ViewHasWorkspace[S] =>
            DocumentViewHandler.instance.activeDocument = Some(wv.workspace)
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

    pack()
  }
}

abstract class WindowImpl[S <: Sys[S]] private (titleExpr: Option[CellView[S#Tx, String]])
  extends Window[S] with WindowHolder[desktop.Window] with DependentMayVeto[S#Tx] {
  impl =>

  def this() = this(None)
  def this(titleExpr: CellView[S#Tx, String]) = this(Some(titleExpr))

  protected def style: desktop.Window.Style = desktop.Window.Regular

  private[this] var windowImpl: WindowImpl.Peer[S] = _
  private[this] val titleObserver = Ref(stm.Disposable.empty[S#Tx])

  final def title        : String        = windowImpl.title
  final def title_=(value: String): Unit = windowImpl.title = value

//  final def dirty        : Boolean        = windowImpl.dirty
//  final def dirty_=(value: Boolean): Unit = windowImpl.dirty = value

//  final def resizable        : Boolean        = windowImpl.resizable
//  final def resizable_=(value: Boolean): Unit = windowImpl.resizable = value

  protected def undecorated: Boolean = false

  final def windowFile        : Option[File]        = windowImpl.file
  final def windowFile_=(value: Option[File]): Unit = windowImpl.file = value

  final protected def bindMenus(entries: (String, Action)*): Unit = windowImpl.bindMenus(entries: _*)

  def setTitleExpr(exprOpt: Option[CellView[S#Tx, String]])(implicit tx: S#Tx): Unit = {
    titleObserver.swap(stm.Disposable.empty).dispose()
    exprOpt.foreach { ex =>
      def update(s: String)(implicit tx: S#Tx): Unit = deferTx { title = s }

      val obs = ex.react { implicit tx => now => update(now) }
      titleObserver() = obs
      update(ex())
    }
  }

  final def init()(implicit tx: S#Tx): this.type = {
    view match {
      case wv: ViewHasWorkspace[S] => wv.workspace.addDependent(impl)
      case _ =>
    }

    deferTx(initGUI0())
    setTitleExpr(titleExpr)
    this
  }

  private def initGUI0(): Unit = {
    val f       = new WindowImpl.Peer(view, impl, undoRedoActions, style, undecorated = undecorated)
    window      = f
    windowImpl  = f
    Window.attach(f, this)
    val p = placement
    desktop.Util.placeWindow(f, p.horizontal, p.vertical, p.padding)
    f.front()

    initGUI()
  }

  protected def initGUI(): Unit = ()

  /** Subclasses may override this. The tuple is (horizontal, vertical, padding) position.
    * By default it centers the window, i.e. `(0.5f, 0.5f, 20)`.
    */
  protected def placement: WindowPlacement = WindowPlacement.default

  /** Subclasses may override this. By default this always returns `None`.
    */
  def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] = None

  /** Subclasses may override this. */
  protected def undoRedoActions: Option[(Action, Action)] =
    view match {
      case ev: View.Editable[S] =>
        val mgr = ev.undoManager
        Some(mgr.undoAction -> mgr.redoAction)
      case _ => None
    }

  private[this] val _wasDisposed = Ref(false)

  protected def performClose(): Future[Unit] =
    view match {
      case cv: View.Cursor[S] =>
        import cv.cursor

        def complete()(implicit tx: S#Tx): Unit = {
          deferTx(windowImpl.visible = false)
          dispose()
        }

        def succeed()(implicit tx: S#Tx): Future[Unit] = {
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
      fut.foreach { _ =>
        _wasDisposed.single.set(true)
      }
    }
  }

  def pack(): Unit = windowImpl.pack()

  protected final def wasDisposed(implicit tx: S#Tx): Boolean = _wasDisposed.get(tx.peer)

  def dispose()(implicit tx: S#Tx): Unit = {
    titleObserver().dispose()

    view match {
      case wv: ViewHasWorkspace[S] => wv.workspace.removeDependent(this)
      case _ =>
    }

    view.dispose()

    deferTx {
      window.dispose()
    }

    _wasDisposed() = true
  }
}