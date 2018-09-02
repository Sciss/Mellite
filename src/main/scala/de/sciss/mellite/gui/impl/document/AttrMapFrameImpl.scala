/*
 *  AttrMapFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.gui
package impl
package document

import javax.swing.JComponent
import javax.swing.undo.UndoableEdit
import de.sciss.desktop
import de.sciss.desktop.KeyStrokes.menu1
import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.mellite.gui.impl.component.CollectionViewImpl
import de.sciss.synth.proc.Workspace

import scala.swing.event.Key
import scala.swing.{Action, Component}

object AttrMapFrameImpl {
  def apply[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                      cursor: stm.Cursor[S]): AttrMapFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val contents  = AttrMapView[S](obj)
    val view      = new ViewImpl[S](contents)
    view.init()
    val name      = CellView.name(obj)
    val res       = new FrameImpl[S](tx.newHandle(obj), view, name = name)
    res.init()
    res
  }

  private final class ViewImpl[S <: Sys[S]](val peer: AttrMapView[S])
                                           (implicit val cursor: stm.Cursor[S], val undoManager: UndoManager)
    extends CollectionViewImpl[S] {

    impl =>

    def workspace: Workspace[S] = peer.workspace

    def dispose()(implicit tx: S#Tx): Unit = ()

    protected lazy val actionDelete: Action = Action(null) {
      val sel = peer.selection
      val edits: List[UndoableEdit] = cursor.step { implicit tx =>
        val obj0 = peer.obj
        sel.map { case (key, _) =>
          EditAttrMap(name = s"Delete Attribute '$key'", obj = obj0, key = key, value = None)
        }
      }
      val ceOpt = CompoundEdit(edits, "Delete Attributes")
      ceOpt.foreach(undoManager.add)
    }

    protected type InsertConfig = String

    protected def prepareInsert(f: ObjView.Factory): Option[String] = peer.queryKey()

    protected def editInsert(f: ObjView.Factory, xs: List[Obj[S]], key: String)(implicit tx: S#Tx): Option[UndoableEdit] = {
      val edits = xs.map { value =>
        val editName = s"Create Attribute '$key'"
        EditAttrMap(name = editName, obj = peer.obj, key = key, value = Some(value))
      }
      CompoundEdit(edits, "Create Attributes")
    }

    protected def initGUI2(): Unit = {
      peer.addListener {
        case AttrMapView.SelectionChanged(_, sel) =>
          selectionChanged(sel.map(_._2))
      }
    }

    protected def selectedObjects: List[ObjView[S]] = peer.selection.map(_._2)
  }

  private final class FrameImpl[S <: Sys[S]](objH: stm.Source[S#Tx, Obj[S]], val view: ViewImpl[S],
                                             name: CellView[S#Tx, String])
                                       (implicit cursor: stm.Cursor[S], undoManager: UndoManager)
    extends WindowImpl[S](name.map(n => s"$n : Attributes"))
    with AttrMapFrame[S] {

    override protected def style: desktop.Window.Style = desktop.Window.Auxiliary

    def contents: AttrMapView[S] = view.peer

    def component: Component = contents.component

    protected def selectedObjects: List[ObjView[S]] = contents.selection.map(_._2)

    // XXX TODO --- there should be a general mechanism for
    // binding actions even if the menu bar is absent.
    override protected def initGUI(): Unit = {
      super.initGUI()

      val c           = component
      val actionClose = Action(null)(handleClose())
      val am          = c.peer.getActionMap
      val im          = c.peer.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      am.put("file.close", actionClose.peer)
      im.put(menu1 + Key.W, "file.close")
      undoRedoActions.foreach { case (undo, redo) =>
        am.put("edit.undo", undo.peer)
        im.put(MenuBar.keyUndo, "edit.undo")
        am.put("edit.redo", redo.peer)
        im.put(MenuBar.keyRedo, "edit.redo")
      }
    }

    protected lazy val actionDelete: Action = Action(null) {
      val sel = contents.selection
      if (sel.nonEmpty) {
        val editOpt = cursor.step { implicit tx =>
          val ed1 = sel.map { case (key, _) =>
            EditAttrMap(name = s"Remove Attribute '$key'", objH(), key = key, value = None)
          }
          CompoundEdit(ed1, "Remove Attributes")
        }
        editOpt.foreach(undoManager.add)
      }
    }
  }
}