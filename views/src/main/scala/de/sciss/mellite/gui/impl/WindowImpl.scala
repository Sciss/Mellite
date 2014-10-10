/*
 *  WindowImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
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
import scala.swing.Action
import de.sciss.lucre.event.Sys
import de.sciss.file._
import de.sciss.synth.Optional
import de.sciss.lucre.swing.{View, Window, deferTx, requireEDT}

object WindowImpl {
  final val WindowKey = "de.sciss.mellite.Window"

  private final class Peer[S <: Sys[S]](view: View[S], impl: WindowImpl[S],
                                        undoRedoActions: Option[(Action, Action)],
                                        override val style: desktop.Window.Style)
    extends desktop.impl.WindowImpl {

    def handler = Application.windowHandler

    // bindMenu("action.window-shot", new ActionWindowShot(this))

    // addAction("window-shot", new ActionWindowShot(this))

    view match {
      case fv: View.File => file = Some(fv.file)
      case _ =>
    }
    file.map(_.base).foreach(title = _)

    contents  = view.component
    closeOperation = desktop.Window.CloseIgnore
    reactions += {
      case desktop.Window.Closing  (_) => impl.handleClose()
      case desktop.Window.Activated(_) =>
        view match {
          case wv: ViewHasWorkspace[S] =>
            DocumentViewHandler.instance.activeDocument = Some(wv.workspace)
          case _ =>
        }
    }

    bindMenu("file.close", Action(null)(impl.handleClose()))

    undoRedoActions.foreach { case (undo, redo) =>
      bindMenus(
        "edit.undo" -> undo,
        "edit.redo" -> redo
      )
    }

    pack()
  }
}

//abstract class WindowImpl0[S <: Sys[S], S1 <: Sys[S1]](title0: Optional[String] = None)
//  extends Window[S] with WindowHolder[desktop.Window] {
//}

abstract class WindowImpl[S <: Sys[S]](title0: Optional[String] = None)
  extends Window[S] with WindowHolder[desktop.Window] {

  impl =>

  protected def style: desktop.Window.Style = desktop.Window.Regular

  // final def window: desktop.Window = component
  private var windowImpl: WindowImpl.Peer[S] = _

  final protected def title        : String        = windowImpl.title
  final protected def title_=(value: String): Unit = windowImpl.title = value

  final protected def dirty        : Boolean        = windowImpl.dirty
  final protected def dirty_=(value: Boolean): Unit = windowImpl.dirty = value

  final protected def windowFile        : Option[File]        = windowImpl.file
  final protected def windowFile_=(value: Option[File]): Unit = windowImpl.file = value

  final protected def bindMenus(entries: (String, Action)*): Unit = windowImpl.bindMenus(entries: _*)

  final def init()(implicit tx: S#Tx): Unit = {
    view match {
      case wv: ViewHasWorkspace[S] => wv.workspace.addDependent(impl)
      case _ =>
    }
    deferTx(initGUI0())
  }

  private def initGUI0(): Unit = {
    val f       = new WindowImpl.Peer(view, impl, undoRedoActions, style)
    title0.foreach(f.title_=)
    window      = f
    windowImpl  = f
    val (ph, pv, pp) = placement
    desktop.Util.placeWindow(f, ph, pv, pp)
    f.front()

    // so that the component may find a "window ancestor"
    view.component.peer.putClientProperty(WindowImpl.WindowKey, f)

    initGUI()
  }

  protected def initGUI(): Unit = ()

  /** Subclasses may override this. The tuple is (horizontal, vertical, padding) position.
    * By default it centers the window, i.e. `(0.5f, 0.5f, 20)`.
    */
  protected def placement: (Float, Float, Int) = (0.5f, 0.5f, 20)

  /** Subclasses may override this. If this method returns `true`, the window may be closed,
    * otherwise a closure is aborted. By default this always returns `true`.
    */
  protected def checkClose(): Boolean = true

  /** Subclasses may override this. */
  protected def undoRedoActions: Option[(Action, Action)] =
    view match {
      case ev: View.Editable[S] =>
        val mgr = ev.undoManager
        Some(mgr.undoAction -> mgr.redoAction)
      case _ => None
    }

  private var didClose = false
  private def disposeFromGUI(): Unit = if (!didClose) {
    requireEDT()
    performClose()
    didClose = true
  }

  protected def performClose(): Unit =
    view match {
      case cv: View.Cursor[S] => cv.cursor.step { implicit tx =>
        dispose()
      }
      case _ =>
    }

  final def handleClose(): Unit = {
    requireEDT()
    if (checkClose()) disposeFromGUI()
  }

  def pack(): Unit = windowImpl.pack()

  def dispose()(implicit tx: S#Tx): Unit = {
    view match {
      case wv: ViewHasWorkspace[S] => wv.workspace.removeDependent(this)
      case _ =>
    }

    view.dispose()
    deferTx {
      window.dispose()
      didClose = true
    }
  }
}