/*
 *  AttrMapFrameImpl.scala
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

package de.sciss.mellite.impl.document

import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.Obj
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.impl.WorkspaceWindow
import de.sciss.mellite.impl.component.{CollectionViewImpl, NoMenuBarActions}
import de.sciss.mellite.{AttrMapFrame, AttrMapView, ObjView, ViewState}
import de.sciss.proc.Universe

import javax.swing.undo.UndoableEdit
import scala.swing.{Action, Component}

object AttrMapFrameImpl {
  def apply[T <: Txn[T]](obj: Obj[T])(implicit tx: T, universe: Universe[T]): AttrMapFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val contents  = AttrMapView[T](obj)
    val view      = new ViewImpl[T](contents)
    view.init()
    val name      = CellView.name(obj)
    val res       = new FrameImpl[T](/*tx.newHandle(obj),*/ view, name = name)
    res.init()
    res
  }

  private final class ViewImpl[T <: Txn[T]](val peer: AttrMapView[T])
                                           (implicit val undoManager: UndoManager)
    extends CollectionViewImpl[T] {

    impl =>

    override def obj(implicit tx: T): Obj[T] = peer.obj

    override def viewState: Set[ViewState] = peer.viewState

    //    def workspace: Workspace[T] = peer.workspace
    val universe: Universe[T] = peer.universe

    def dispose()(implicit tx: T): Unit = ()

    protected lazy val actionDelete: Action = Action(null) {
      val sel = peer.selection
      val edits: List[UndoableEdit] = cursor.step { implicit tx =>
        val obj0 = peer.receiver
        sel.map { case (key, _) =>
          EditAttrMap(name = s"Delete Attribute '$key'", obj = obj0, key = key, value = None)
        }
      }
      val ceOpt = CompoundEdit(edits, "Delete Attributes")
      ceOpt.foreach(undoManager.add)
    }

    protected type InsertConfig = String

    protected def prepareInsertDialog(f: ObjView.Factory): Option[String] = peer.queryKey()

    protected def prepareInsertCmdLine(args: List[String]): Option[(String, List[String])] = args match {
      case key :: rest  => Some((key, rest))
      case _            => None
    }

    protected def editInsert(f: ObjView.Factory, xs: List[Obj[T]], key: String)(implicit tx: T): Option[UndoableEdit] = {
      val editOpt = xs.lastOption.map { value =>
        val editName = s"Create Attribute '$key'"
        EditAttrMap(name = editName, obj = peer.receiver, key = key, value = Some(value))
      }
      editOpt
//      CompoundEdit(edits, "Create Attributes")
    }

    protected def initGUI2(): Unit = {
      peer.addListener {
        case AttrMapView.SelectionChanged(_, sel) =>
          selectionChanged(sel.map(_._2))
      }
    }

    protected def selectedObjects: List[ObjView[T]] = peer.selection.map(_._2)
  }

  private final class FrameImpl[T <: Txn[T]](/*objH: Source[T, Obj[T]],*/ val view: ViewImpl[T],
                                             name: CellView[T, String])
//                                       (implicit undoManager: UndoManager)
    extends WorkspaceWindow[T](name.map(n => s"$n : Attributes"))
    with AttrMapFrame[T] with NoMenuBarActions {

    override protected def style: desktop.Window.Style = desktop.Window.Auxiliary

    def contents: AttrMapView[T] = view.peer

    def component: Component = contents.component

//    protected def selectedObjects: List[ObjView[T]] = contents.selection.map(_._2)

    override protected def initGUI(): Unit = {
      super.initGUI()
      initNoMenuBarActions(component)
    }

//    protected lazy val actionDelete: Action = Action(null) {
//      val sel = contents.selection
//      if (sel.nonEmpty) {
//        import view.universe.cursor
//        val editOpt = cursor.step { implicit tx =>
//          val ed1 = sel.map { case (key, _) =>
//            EditAttrMap(name = s"Remove Attribute '$key'", objH(), key = key, value = None)
//          }
//          CompoundEdit(ed1, "Remove Attributes")
//        }
//        editOpt.foreach(undoManager.add)
//      }
//    }
  }
}