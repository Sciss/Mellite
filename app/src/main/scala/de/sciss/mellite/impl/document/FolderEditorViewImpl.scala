/*
 *  FolderEditorViewImpl.scala
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

package de.sciss.mellite.impl.document

import de.sciss.desktop
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{KeyStrokes, UndoManager, Window}
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Folder, Obj, StringObj}
import de.sciss.mellite.edit.{EditFolderInsertObj, EditFolderRemoveObj}
import de.sciss.mellite.impl.component.CollectionViewImpl
import de.sciss.mellite.{FolderEditorView, FolderView, ObjView}
import de.sciss.swingplus.{GroupPanel, Spinner}
import de.sciss.proc.{ObjKeys, Universe}
import javax.swing.SpinnerNumberModel
import javax.swing.undo.UndoableEdit

import scala.swing.Swing.EmptyIcon
import scala.swing.event.Key
import scala.swing.{Action, Alignment, CheckBox, Dialog, Label, Swing, TextField}

object FolderEditorViewImpl extends FolderEditorView.Companion {
  def install(): Unit =
    FolderEditorView.peer = this

  def apply[T <: Txn[T]](folder: Folder[T])(implicit tx: T, universe: Universe[T],
                                            undoManager: UndoManager): FolderEditorView[T] = {
    val peer  = FolderView(folder)
    val view  = new Impl[T](peer)
    view.init()
  }

  private final class Impl[T <: Txn[T]](val peer: FolderView[T])
                                       (implicit val universe: Universe[T], val undoManager: UndoManager)
    extends CollectionViewImpl[T] with FolderEditorView[T]
  {

    impl =>

    protected type InsertConfig = Unit

    protected def prepareInsertDialog(f: ObjView.Factory): Option[InsertConfig] = Some(())

    protected def prepareInsertCmdLine(args: List[String]): Option[(Unit, List[String])] = Some(((), args))

    protected def editInsert(f: ObjView.Factory, xs: List[Obj[T]], config: InsertConfig)
                            (implicit tx: T): Option[UndoableEdit] = {
      val (parent, idx) = impl.peer.insertionPoint
      val edits: List[UndoableEdit] = xs.zipWithIndex.map { case (x, j) =>
        EditFolderInsertObj(f.prefix, parent, idx + j, x)
      }
      CompoundEdit(edits, "Create Objects")
    }

    def dispose()(implicit tx: T): Unit =
      peer.dispose()

    protected lazy val actionDelete: Action = Action(null) {
      val sel = peer.selection
      val edits: List[UndoableEdit] = cursor.step { implicit tx =>
        sel.flatMap { nodeView =>
          val parent = nodeView.parent
          val childH  = nodeView.modelData
          val child   = childH()
          val idx     = parent.indexOf(child)
          if (idx < 0) {
            println("WARNING: Parent folder of object not found")
            None
          } else {
            val edit = EditFolderRemoveObj[T](nodeView.renderData.humanName, parent, idx, child)
            Some(edit)
          }
        }
      }
      val ceOpt = CompoundEdit(edits, "Delete Elements")
      ceOpt.foreach(undoManager.add)
    }

    protected def initGUI2(): Unit = {
      peer.addListener {
        case FolderView.SelectionChanged(_, sel) =>
          selectionChanged(sel.map(_.renderData))
          actionDuplicate.enabled = sel.nonEmpty
      }
    }

    lazy val actionDuplicate: Action = new Action("Duplicate...") {
      accelerator = Some(KeyStrokes.menu1 + Key.D)
      enabled     = impl.peer.selection.nonEmpty

      private var appendText  = "-1"
      private var count       = 1
      private var append      = true

      private def incLast(x: String, c: Int): String = {
        val p = "\\d+".r
        val m = p.pattern.matcher(x)
        var start = -1
        var end   = -1
        while (m.find()) {
          start = m.start()
          end   = m.end()
        }
        if (start < 0) x else {
          val (pre, tmp) = x.splitAt(start)
          val (old, suf) = tmp.splitAt(end - start)
          s"$pre${old.toInt + c}$suf"
        }
      }

      def apply(): Unit = {
        val sel     = impl.peer.selection
        val numSel  = sel.size
        if (numSel == 0) return

        sel.map(view => view.renderData.name)

        val txtInfo = s"Duplicate ${if (numSel == 1) s"'${sel.head.renderData.name}'" else s"$numSel Objects"}"
        val lbInfo  = new Label(txtInfo)
        lbInfo.border = Swing.EmptyBorder(0, 0, 8, 0)
        val ggName  = new TextField(6)
        ggName.text = appendText
        val mCount  = new SpinnerNumberModel(count, 1, 0x4000, 1)
        val ggCount = new Spinner(mCount)
        val lbName  = new CheckBox("Append to Name:")
        lbName.selected = append
        val lbCount = new Label("Number of Copies:" , EmptyIcon, Alignment.Right)

        val box = new GroupPanel {
          horizontal  = Par(
            lbInfo,
            Seq(Par(Trailing)(lbCount, lbName), Par(ggName , ggCount))
          )
          vertical = Seq(lbInfo, Par(Baseline)(lbName, ggName ), Par(Baseline)(lbCount, ggCount))
        }

        val pane = desktop.OptionPane.confirmation(box, optionType = Dialog.Options.OkCancel,
          messageType = Dialog.Message.Question, focus = Some(ggCount))
        pane.title  = "Duplicate"
        val window  = Window.find(component)
        val res     = pane.show(window)

        import de.sciss.equal.Implicits._
        if (res === Dialog.Result.Ok) {
          // save state
          count       = mCount.getNumber.intValue()
          appendText  = ggName.text
          append      = lbName.selected

          // lazy val regex = "\\d+".r // do _not_ catch leading minus character

          val edits: List[UndoableEdit] = cursor.step { implicit tx =>
            sel.flatMap { nodeView =>
              val p       = nodeView.parent
              val orig    = nodeView.modelData()
              val idx     = p.indexOf(orig)
              val copies  = List.tabulate(count) { n =>
                val cpy = Obj.copy(orig)
                if (append) {
                  val suffix = incLast(appendText, n)
                  orig.attr.$[StringObj](ObjKeys.attrName).foreach { oldName =>
                    // val imp = ExprImplicits[T]
//                    import expr.Ops._
                    val newName = oldName ++ suffix
                    cpy.attr.put(ObjKeys.attrName, StringObj.newVar(newName))
                  }
                  // cpy.attr.name = s"${cpy.attr.name}$suffix"
                }
                EditFolderInsertObj("Copy", p, idx + n + 1, cpy)
              }
              copies
            }
          }
          val editOpt = CompoundEdit(edits, txtInfo)
          editOpt.foreach(undoManager.add)
        }
      }
    }

    def selectedObjects: List[ObjView[T]] = peer.selection.map(_.renderData)
  }
}
