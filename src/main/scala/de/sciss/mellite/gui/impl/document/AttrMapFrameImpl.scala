/*
 *  AttrMapFrameImpl.scala
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

package de.sciss.mellite.gui.impl.document

import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ObjView
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.mellite.gui.impl.WindowImpl
import de.sciss.mellite.gui.impl.component.{CollectionViewImpl, NoMenuBarActions}
import de.sciss.mellite.gui.{AttrMapFrame, AttrMapView}
import de.sciss.synth.proc.Universe
import javax.swing.undo.UndoableEdit

import scala.swing.{Action, Component}

object AttrMapFrameImpl {
  def apply[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx, universe: Universe[S]): AttrMapFrame[S] = {
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
                                           (implicit val undoManager: UndoManager)
    extends CollectionViewImpl[S] {

    impl =>

//    def workspace: Workspace[S] = peer.workspace
    val universe: Universe[S] = peer.universe

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

    protected def prepareInsertDialog(f: ObjView.Factory): Option[String] = peer.queryKey()

    protected def prepareInsertCmdLine(args: List[String]): Option[(String, List[String])] = args match {
      case key :: rest  => Some((key, rest))
      case _            => None
    }

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
                                       (implicit undoManager: UndoManager)
    extends WindowImpl[S](name.map(n => s"$n : Attributes"))
    with AttrMapFrame[S] with NoMenuBarActions {

    override protected def style: desktop.Window.Style = desktop.Window.Auxiliary

    def contents: AttrMapView[S] = view.peer

    def component: Component = contents.component

    protected def selectedObjects: List[ObjView[S]] = contents.selection.map(_._2)

    override protected def initGUI(): Unit = {
      super.initGUI()
      initNoMenuBarActions(component)
    }

    protected lazy val actionDelete: Action = Action(null) {
      val sel = contents.selection
      if (sel.nonEmpty) {
        import view.universe.cursor
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