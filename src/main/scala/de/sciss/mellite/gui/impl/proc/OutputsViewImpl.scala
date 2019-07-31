/*
 *  OutputsViewImpl.scala
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

package de.sciss.mellite.gui.impl.proc

import java.awt.datatransfer.Transferable

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{OptionPane, UndoManager, Window}
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.DragAndDrop
import de.sciss.mellite.gui.edit.{EditAddProcOutput, EditRemoveProcOutput}
import de.sciss.mellite.gui.impl.MapViewImpl
import de.sciss.mellite.gui.impl.component.DragSourceButton
import de.sciss.mellite.gui.{GUI, MapView, ObjListView, ProcOutputsView}
import de.sciss.synth.proc.{Proc, Universe}
import javax.swing.undo.UndoableEdit

import scala.swing.Swing.HGlue
import scala.swing.{Action, BoxPanel, Button, Component, FlowPanel, Orientation, ScrollPane}

object OutputsViewImpl {
  def apply[S <: Sys[S]](obj: Proc[S])(implicit tx: S#Tx, universe: Universe[S],
                                       undoManager: UndoManager): ProcOutputsView[S] = {
    val list0 = obj.outputs.iterator.map { out =>
      (out.key, ObjListView(out))
    }  .toIndexedSeq

    new Impl(tx.newHandle(obj)) {
      protected val observer: Disposable[S#Tx] = obj.changed.react { implicit tx =>upd =>
        upd.changes.foreach {
          case Proc.OutputAdded  (out) => attrAdded(out.key, out)
          case Proc.OutputRemoved(out) => attrRemoved(out.key)
          case _ => // graph change
        }
      }

      init(list0)
    }
  }

  private abstract class Impl[S <: Sys[S]](objH: stm.Source[S#Tx, Proc[S]])
                                       (implicit universe: Universe[S],
                                        undoManager: UndoManager)
    extends MapViewImpl[S, ProcOutputsView[S]] with ProcOutputsView[S] with ComponentHolder[Component] { impl =>

    protected final def editRenameKey(before: String, now: String, value: Obj[S])(implicit tx: S#Tx): Option[UndoableEdit] = None
    protected final def editImport(key: String, value: Obj[S], isInsert: Boolean)(implicit tx: S#Tx): Option[UndoableEdit] = None

    override protected def keyEditable: Boolean = false
    override protected def showKeyOnly: Boolean = true

    private object ActionAdd extends Action("Out") {
      def apply(): Unit = {
        val key0  = "out"
        val tpe   = s"${title}put"
        val opt   = OptionPane.textInput(message = s"$tpe Name", initial = key0)
        opt.title = s"Add $tpe"
        opt.show(Window.find(component)).foreach { key =>
          val edit = cursor.step { implicit tx =>
            EditAddProcOutput(objH(), key = key)
          }
          undoManager.add(edit)
        }
      }
    }

    private lazy val actionRemove = Action(null) {
      selection.headOption.foreach { case (key, _ /* view */) =>
        val editOpt = cursor.step { implicit tx =>
//          val obj     = objH()
          val edits3: List[UndoableEdit] = Nil
//            outputs.get(key).fold(List.empty[UndoableEdit]) { thisOutput =>
//            val edits1 = thisOutput.iterator.toList.collect {
//              case Output.Link.Output(thatOutput) =>
//                val source  = thisOutput
//                val sink    = thatOutput
//                EditRemoveOutputLink(source = source, sink = sink)
//            }
//            edits1
//          }
          edits3.foreach(e => println(e.getPresentationName))
          val editMain = EditRemoveProcOutput(objH(), key = key)
          CompoundEdit(edits3 :+ editMain, "Remove Output")
        }
        editOpt.foreach(undoManager.add)
      }
    }

    private var ggDrag: Button = _

    private def selectionUpdated(): Unit = {
      val enabled = table.selection.rows.nonEmpty // .pages.nonEmpty
      actionRemove.enabled  = enabled
      ggDrag      .enabled  = enabled
    }

    final protected def guiInit1(scroll: ScrollPane): Unit = {
      // tab.preferredSize = (400, 100)
      val ggAddOut  = GUI.toolButton(ActionAdd   , raphael.Shapes.Plus , "Add Output"   )
      val ggDelete  = GUI.toolButton(actionRemove, raphael.Shapes.Minus, "Remove Output")
      ggDrag        = new DragSourceButton() {
        protected def createTransferable(): Option[Transferable] =
          selection.headOption.map { case (key, _ /* view */) =>
            DragAndDrop.Transferable(ProcOutputsView.flavor)(ProcOutputsView.Drag[S](
              universe, objH, key))
          }
      }

      selectionUpdated()
      addListener {
        case MapView.SelectionChanged(_, _) => selectionUpdated()
      }

      val box = new BoxPanel(Orientation.Vertical) {
        contents += scroll // tab
        contents += new FlowPanel(ggAddOut, ggDelete, ggDrag, HGlue)
      }
      box.preferredSize = box.minimumSize
      component = box
    }
  }
}