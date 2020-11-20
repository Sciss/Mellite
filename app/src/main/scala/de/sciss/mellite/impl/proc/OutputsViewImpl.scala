/*
 *  OutputsViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.proc

import java.awt.datatransfer.Transferable
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{OptionPane, UndoManager, Window}
import de.sciss.icons.raphael
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, Obj, Source}
import de.sciss.mellite.edit.{EditAddProcOutput, EditRemoveProcOutput}
import de.sciss.mellite.impl.MapViewImpl
import de.sciss.mellite.impl.component.DragSourceButton
import de.sciss.mellite.{DragAndDrop, GUI, MapView, ObjListView, ObjView, ProcOutputsView}
import de.sciss.proc.{Proc, Universe}

import javax.swing.undo.UndoableEdit
import scala.swing.Swing.HGlue
import scala.swing.{Action, BoxPanel, Button, Component, FlowPanel, Orientation, ScrollPane}

object OutputsViewImpl {
  def apply[T <: Txn[T]](obj: Proc[T])(implicit tx: T, universe: Universe[T],
                                       undoManager: UndoManager): ProcOutputsView[T] = {
    val list0 = obj.outputs.iterator.map { out =>
      (out.key, ObjListView(out))
    }  .toIndexedSeq

    new Impl(tx.newHandle(obj)) {
      protected val observer: Disposable[T] = obj.changed.react { implicit tx =>upd =>
        upd.changes.foreach {
          case Proc.OutputAdded  (out) => attrAdded(out.key, out)
          case Proc.OutputRemoved(out) => attrRemoved(out.key)
          case _ => // graph change
        }
      }

      init(list0)
    }
  }

  private abstract class Impl[T <: Txn[T]](objH: Source[T, Proc[T]])
                                       (implicit universe: Universe[T],
                                        undoManager: UndoManager)
    extends MapViewImpl[T, ProcOutputsView[T]] with ProcOutputsView[T] with ComponentHolder[Component] { impl =>

    protected final def editRenameKey(before: String, now: String, value: Obj[T])
                                     (implicit tx: T): Option[UndoableEdit] = None

    protected final def editImport(key: String, value: Obj[T], context: Set[ObjView.Context[T]], isInsert: Boolean)
                                  (implicit tx: T): Option[UndoableEdit] = None

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
            DragAndDrop.Transferable(ProcOutputsView.flavor)(ProcOutputsView.Drag[T](
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