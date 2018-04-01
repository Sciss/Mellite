/*
 *  FolderFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
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
package document

import javax.swing.SpinnerNumberModel
import javax.swing.undo.UndoableEdit

import de.sciss.desktop
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{KeyStrokes, Menu, UndoManager, Window}
import de.sciss.lucre.expr.StringObj
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.{expr, stm}
import de.sciss.mellite.gui.edit.{EditFolderInsertObj, EditFolderRemoveObj}
import de.sciss.mellite.gui.impl.component.CollectionViewImpl
import de.sciss.swingplus.{GroupPanel, Spinner}
import de.sciss.synth.proc.{Folder, ObjKeys, Workspace}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.swing.Swing.EmptyIcon
import scala.swing.event.Key
import scala.swing.{Action, Alignment, CheckBox, Component, Dialog, Label, SequentialContainer, Swing, TextField}

object FolderFrameImpl {
  def apply[S <: Sys[S]](name: CellView[S#Tx, String],
                         folder: Folder[S],
                         isWorkspaceRoot: Boolean)(implicit tx: S#Tx,
                         workspace: Workspace[S], cursor: stm.Cursor[S]): FolderFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val folderView      = FolderView(folder)
//    val interceptQuit   = isWorkspaceRoot && workspace.folder.isEmpty // i.e. an in-memory workspace
    val view            = new ViewImpl[S](folderView)
    view.init()

    val res = new FrameImpl[S](view, name = name, isWorkspaceRoot = isWorkspaceRoot /* , interceptQuit = interceptQuit */)
    res.init()
    res
  }

  def addDuplicateAction[S <: Sys[S]](w: WindowImpl[S], action: Action): Unit =
    Application.windowHandler.menuFactory.get("edit") match {
      case Some(mEdit: Menu.Group) =>
        val itDup = Menu.Item("duplicate", action)
        mEdit.add(Some(w.window), itDup)    // XXX TODO - should be insert-after "Select All"
      case _ =>
    }

//  private def addImportJSONAction[S <: Sys[S]](w: WindowImpl[S], action: Action): Unit =
//    Application.windowHandler.menuFactory.get("actions") match {
//      case Some(mEdit: Menu.Group) =>
//        val itDup = Menu.Item("import-json", action)
//        mEdit.add(Some(w.window), itDup)
//      case _ =>
//    }

  private final class FrameImpl[S <: Sys[S]](val view: ViewImpl[S], name: CellView[S#Tx, String],
                                             isWorkspaceRoot: Boolean /* , interceptQuit: Boolean */)
    extends WindowImpl[S](name) with FolderFrame[S] /* with Veto[S#Tx] */ {

    def workspace : Workspace [S] = view.workspace
    def folderView: FolderView[S] = view.peer

    def bottomComponent: Component with SequentialContainer = view.bottomComponent

//    private[this] var quitAcceptor = Option.empty[() => Boolean]

    override protected def initGUI(): Unit = {
      addDuplicateAction (this, view.actionDuplicate)
      // addImportJSONAction(this, view.actionImportJSON)
      // if (interceptQuit) quitAcceptor = ... // Some(Desktop.addQuitAcceptor(checkClose()))
    }

    override protected def placement = WindowPlacement(0.5f, 0.0f)

//    override protected def checkClose(): Boolean = !interceptQuit ||
//      ActionCloseAllWorkspaces.check(workspace, Some(window))

//    override protected def performClose(): Future[Unit] =
//      if (isWorkspaceRoot) {
//        ActionCloseAllWorkspaces.close(workspace)
//      } else {
//        super.performClose()
//      }

//    override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
//      if      (!isWorkspaceRoot ) None
////      else if (interceptQuit    ) Some(this)
//      else ... // collectVetos()

    override protected def performClose(): Future[Unit] = if (!isWorkspaceRoot) super.performClose() else {
      log(s"Closing workspace ${workspace.folder}")
      ActionCloseAllWorkspaces.tryClose(workspace, Some(window))
    }

//    private def collectVetos()(implicit tx: S#Tx): Option[Veto[S#Tx]] = {
//      val list: List[Veto[S#Tx]] = workspace.dependents.flatMap {
//        case mv: DependentMayVeto[S#Tx] if mv != self => mv.prepareDisposal()
//      } (breakOut)
//
//      list match {
//        case Nil => None
//        case _ =>
//          val res = new Veto[S#Tx] {
//            def vetoMessage(implicit tx: S#Tx): String =
//              list.map(_.vetoMessage).mkString("\n")
//
//            def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] = ...
//          }
//          Some(res)
//      }
//    }

//    private def vetoMessageNothing = "Nothing to veto."
//
//    def vetoMessage(implicit tx: S#Tx): String =
//      if      (!isWorkspaceRoot ) vetoMessageNothing
//      else if (interceptQuit    ) ActionCloseAllWorkspaces.messageClosingInMemory
//      else collectVetos() match {
//        case None     => vetoMessageNothing
//        case Some(v)  => v.vetoMessage
//      }
//
//    /** Attempts to resolve the veto condition by consulting the user.
//      *
//      * @return successful future if the situation is resolved, e.g. the user agrees to
//      *         proceed with the operation. failed future if the veto is upheld, and
//      *         the caller should abort the operation.
//      */
//    def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] = ...
//
//    override def dispose()(implicit tx: S#Tx): Unit = if (!wasDisposed) {
//      super.dispose()
////      if (isWorkspaceRoot) ActionCloseAllWorkspaces.close(workspace)
//      deferTx {
//        quitAcceptor.foreach(Desktop.removeQuitAcceptor)
//      }
//    }
  }

  final class ViewImpl[S <: Sys[S]](val peer: FolderView[S])
                                   (implicit val workspace: Workspace[S],
                                    val cursor: stm.Cursor[S], val undoManager: UndoManager)
    extends CollectionViewImpl[S]
    {

    impl =>

    protected type InsertConfig = Unit

    protected def prepareInsert(f: ObjView.Factory): Option[InsertConfig] = Some(())

    protected def editInsert(f: ObjView.Factory, xs: List[Obj[S]], config: InsertConfig)
                            (implicit tx: S#Tx): Option[UndoableEdit] = {
      val (parent, idx) = impl.peer.insertionPoint
      val edits: List[UndoableEdit] = xs.zipWithIndex.map { case (x, j) =>
        EditFolderInsertObj(f.prefix, parent, idx + j, x)
      } (breakOut)
      CompoundEdit(edits, "Create Objects")
    }

    def dispose()(implicit tx: S#Tx): Unit =
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
//            implicit val folderSer = Folder.serializer[S]
            val edit = EditFolderRemoveObj[S](nodeView.renderData.humanName, parent, idx, child)
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

//    lazy val actionImportJSON: Action = new Action("Import Mellite v0.3.x JSON...") {
//      def apply(): Unit = {
//        FileDialog.open(title = title.substring(0, title.length - 3)).show(None).foreach { f =>
////          val fi = new FileInputStream(f)
////          try {
////            val bytes = new Array[Byte](fi.available())
////            fi.read(bytes)
////            val json  = Json.parse(bytes)
//            cursor.step { implicit tx => ImportJSON[S](impl.peer.root(), f /* json */) }
////          } finally {
////            fi.close()
////          }
//        }
//      }
//    }

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
                    // val imp = ExprImplicits[S]
                    import expr.Ops._
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

    def selectedObjects: List[ObjView[S]] = peer.selection.map(_.renderData)
  }
}