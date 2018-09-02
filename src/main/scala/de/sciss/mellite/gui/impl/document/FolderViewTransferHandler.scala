/*
 *  FolderViewTransferHandler.scala
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

package de.sciss.mellite
package gui
package impl.document

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.io.File

import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Copy, Folder, Obj, Sys, Txn}
import de.sciss.lucre.swing.TreeTableView
import de.sciss.mellite.gui.edit.{EditFolderInsertObj, EditFolderRemoveObj}
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.synth.proc.Workspace
import javax.swing.TransferHandler.TransferSupport
import javax.swing.undo.UndoableEdit
import javax.swing.{JComponent, TransferHandler}

import scala.language.existentials
import scala.util.Try

/** Mixin that provides a transfer handler for the folder view. */
trait FolderViewTransferHandler[S <: Sys[S]] { fv =>
  protected def workspace       : Workspace[S]
  protected def undoManager     : UndoManager
  protected implicit def cursor : stm.Cursor[S]

  protected def treeView: TreeTableView[S, Obj[S], Folder[S], ListObjView[S]]
  protected def selection: FolderView.Selection[S]

  protected def findLocation(f: File): Option[ActionArtifactLocation.QueryResult[S]]

  protected object FolderTransferHandler extends TransferHandler {
    // ---- export ----

    override def getSourceActions(c: JComponent): Int =
      TransferHandler.COPY | TransferHandler.MOVE | TransferHandler.LINK // dragging only works when MOVE is included. Why?

    override def createTransferable(c: JComponent): Transferable = {
      val sel     = selection
      val trans0  = DragAndDrop.Transferable(FolderView.SelectionFlavor) {
        new FolderView.SelectionDnDData[S](fv.workspace, fv.cursor, sel)
      }
      val trans1 = if (sel.size == 1) {
        val _res = DragAndDrop.Transferable(ListObjView.Flavor) {
          new ListObjView.Drag[S](fv.workspace, fv.cursor, sel.head.renderData)
        }
        DragAndDrop.Transferable.seq(trans0, _res)
      } else trans0

      trans1
    }

    // ---- import ----

    override def canImport(support: TransferSupport): Boolean =
      treeView.dropLocation.exists { tdl =>
        val locOk = tdl.index >= 0 || (tdl.column == 0 && !tdl.path.lastOption.exists(_.isLeaf))
        val res = locOk && {
          if (support.isDataFlavorSupported(FolderView.SelectionFlavor)) {
            val data = support.getTransferable.getTransferData(FolderView.SelectionFlavor)
              .asInstanceOf[FolderView.SelectionDnDData[_]]
            if (data.workspace != workspace) {
              // no linking between sessions
              support.setDropAction(TransferHandler.COPY)
            }
            true
          } else if (support.isDataFlavorSupported(ListObjView.Flavor)) {
            val data = support.getTransferable.getTransferData(ListObjView.Flavor)
              .asInstanceOf[ListObjView.Drag[_]]
            if (data.workspace != workspace) {
              // no linking between sessions
              support.setDropAction(TransferHandler.COPY)
            }
            true
          } else {
            support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
          }
        }
        // println(s"canImport? $res")
        res
      }

    override def importData(support: TransferSupport): Boolean =
      treeView.dropLocation.exists { _ =>
        // println("importData")
        val editOpt: Option[UndoableEdit] = {
          val isFolder  = support.isDataFlavorSupported(FolderView.SelectionFlavor)
          val isList    = support.isDataFlavorSupported(ListObjView.Flavor)

          // println(s"importData -- isFolder $isFolder, isList $isList")
          // println(support.getTransferable.getTransferDataFlavors.mkString("flavors: ", ", ", ""))

          val crossSessionFolder = isFolder &&
            (support.getTransferable.getTransferData(FolderView.SelectionFlavor)
              .asInstanceOf[FolderView.SelectionDnDData[_]].workspace != workspace)
          val crossSessionList = !crossSessionFolder && isList &&
            (support.getTransferable.getTransferData(ListObjView.Flavor)
              .asInstanceOf[ListObjView.Drag[_]].workspace != workspace)

          // println(s"importData -- crossSession ${crossSessionFolder | crossSessionList}")

          if (crossSessionFolder)
            copyFolderData(support)
          else if (crossSessionList) {
            copyListData(support)
          }
          else cursor.step { implicit tx =>
            val pOpt = parentOption
            // println(s"parentOption.isDefined? ${pOpt.isDefined}")
            pOpt.flatMap { case (parent,idx) =>
              if (isFolder)
                insertFolderData(support, parent, idx)
              else if (isList) {
                insertListData(support, parent, idx)
              }
              else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                importFiles(support, parent, idx)
              else None
            }
          }
        }
        editOpt.foreach(undoManager.add)
        editOpt.isDefined
      }

    // ---- folder: link ----

    private def insertFolderData(support: TransferSupport, parent: Folder[S], index: Int)
                                (implicit tx: S#Tx): Option[UndoableEdit] = {
      val data = support.getTransferable.getTransferData(FolderView.SelectionFlavor)
        .asInstanceOf[FolderView.SelectionDnDData[S]]

      insertFolderData1(data.selection, parent, idx = index, dropAction = support.getDropAction)
    }

    // XXX TODO: not sure whether removal should be in exportDone or something
    private def insertFolderData1(sel0: FolderView.Selection[S], newParent: Folder[S], idx: Int, dropAction: Int)
                          (implicit tx: S#Tx): Option[UndoableEdit] = {
      // println(s"insert into $parent at index $idx")

      val sel = FolderView.cleanSelection(sel0)

      import de.sciss.equal.Implicits._
      def isNested(c: Obj[S]): Boolean = c match {
        case objT: Folder[S] =>
          objT === newParent || objT.iterator.toList.exists(isNested)
        case _ => false
      }

      val isMove = dropAction === TransferHandler.MOVE
      val isCopy = dropAction === TransferHandler.COPY

      // make sure we are not moving a folder within itself (it will magically disappear :)
      val sel1 = if (!isMove) sel else sel.filterNot(nv => isNested(nv.modelData()))

      // if we move children within the same folder, adjust the insertion index by
      // decrementing it for any child which is above the insertion index, because
      // we will first remove all children, then re-insert them.
      val idx0 = if (idx >= 0) idx else newParent /* .children */ .size
      val idx1 = if (!isMove) idx0
      else idx0 - sel1.count { nv =>
        val isInNewParent = nv.parent === newParent
        val child = nv.modelData()
        isInNewParent && newParent.indexOf(child) <= idx0
      }

      val editRemove: List[UndoableEdit] = if (!isMove) Nil
      else sel1.flatMap { nv =>
        val parent: Folder[S] = nv.parent
        val childH = nv.modelData
        val idx = parent.indexOf(childH())
        if (idx < 0) {
          println("WARNING: Parent of drag object not found")
          None
        } else {
          val edit = EditFolderRemoveObj[S](nv.renderData.humanName, parent, idx, childH())
          Some(edit)
        }
      }

      val selZip = sel1.zipWithIndex
      val editInsert = if (isCopy) {
        val context = Copy[S, S]
        val res = selZip.map { case (nv, off) =>
          val in  = nv.modelData()
          val out = context(in)
          EditFolderInsertObj[S](nv.renderData.humanName, newParent, idx1 + off, child = out)
        }
        context.finish()
        res
      } else {
        selZip.map { case (nv, off) =>
          EditFolderInsertObj[S](nv.renderData.humanName, newParent, idx1 + off, child = nv.modelData())
        }
      }

      val edits: List[UndoableEdit] = editRemove ++ editInsert
      val name = sel1 match {
        case single :: Nil  => single.renderData.humanName
        case _              => "Elements"
      }

      val prefix = if (isMove) "Move" else if (isCopy) "Copy" else "Link"
      CompoundEdit(edits, s"$prefix $name")
    }
    
    // ---- folder: copy ----

    private def copyFolderData(support: TransferSupport): Option[UndoableEdit] = {
      // cf. https://stackoverflow.com/questions/20982681
      val data  = support.getTransferable.getTransferData(FolderView.SelectionFlavor)
        .asInstanceOf[FolderView.SelectionDnDData[In] forSome { type In <: Sys[In] }]
      copyFolderData1(data)
    }

    private def copyFolderData1[In <: Sys[In]](data: FolderView.SelectionDnDData[In]): Option[UndoableEdit] =
      Txn.copy[In, S, Option[UndoableEdit]] { (txIn: In#Tx, tx: S#Tx) => {
        parentOption(tx).flatMap { case (parent, idx) =>
          val sel0  = data.selection
          val sel   = FolderView.cleanSelection(sel0)
          copyFolderData2(sel, parent, idx)(txIn, tx)
        }
      }} (data.cursor, fv.cursor)

    private def copyFolderData2[In <: Sys[In]](sel: FolderView.Selection[In], newParent: Folder[S], idx: Int)
                                       (implicit txIn: In#Tx, tx: S#Tx): Option[UndoableEdit] = {
      val idx1 = if (idx >= 0) idx else newParent.size

      val context = Copy[In, S]
      val edits = sel.zipWithIndex.map { case (nv, off) =>
        val in  = nv.modelData()
        val out = context(in)
        EditFolderInsertObj[S](nv.renderData.humanName, newParent, idx1 + off, child = out)
      }
      context.finish()
      val name = sel match {
        case single :: Nil  => single.renderData.humanName
        case _              => "Elements"
      }
      CompoundEdit(edits, s"Import $name From Other Workspace")
    }

    // ---- list: link ----

    private def insertListData(support: TransferSupport, parent: Folder[S], index: Int)
                              (implicit tx: S#Tx): Option[UndoableEdit] = {
      val data = support.getTransferable.getTransferData(ListObjView.Flavor)
        .asInstanceOf[ListObjView.Drag[S]]

      val edit = insertListData1(data, parent, idx = index, dropAction = support.getDropAction)
      Some(edit)
    }

    // XXX TODO: not sure whether removal should be in exportDone or something
    private def insertListData1(data: ListObjView.Drag[S], parent: Folder[S], idx: Int, dropAction: Int)
                               (implicit tx: S#Tx): UndoableEdit = {
      val idx1    = if (idx >= 0) idx else parent.size
      val nv      = data.view
      val in      = nv.obj
      val edit    = EditFolderInsertObj[S](nv.name, parent, idx1, child = in)
      edit
    }

    // ---- list: copy ----

    private def copyListData(support: TransferSupport): Option[UndoableEdit] = {
      // cf. https://stackoverflow.com/questions/20982681
      val data  = support.getTransferable.getTransferData(ListObjView.Flavor)
        .asInstanceOf[ListObjView.Drag[In] forSome { type In <: Sys[In] }]
      copyListData1(data)
    }

    private def copyListData1[In <: Sys[In]](data: ListObjView.Drag[In]): Option[UndoableEdit] =
      Txn.copy[In, S, Option[UndoableEdit]] { (txIn: In#Tx, tx: S#Tx) =>
        parentOption(tx).map { case (parent, idx) =>
          implicit val txIn0 : In#Tx = txIn
          implicit val txOut0: S #Tx = tx
          val idx1    = if (idx >= 0) idx else parent.size
          val context = Copy[In, S]
          val nv      = data.view
          val in      = nv.obj
          val out     = context(in)
          val edit    = EditFolderInsertObj[S](nv.name, parent, idx1, child = out)
          context.finish()
          edit
        }
      } (data.cursor, fv.cursor)

    // ---- files ----

    private def importFiles(support: TransferSupport, parent: Folder[S], index: Int)
                           (implicit tx: S#Tx): Option[UndoableEdit] = {
      import scala.collection.JavaConverters._
      val data: List[File] = support.getTransferable.getTransferData(DataFlavor.javaFileListFlavor)
        .asInstanceOf[java.util.List[File]].asScala.toList
      val tup: List[(File, AudioFileSpec)] = data.flatMap { f =>
        Try(AudioFile.readSpec(f)).toOption.map(f -> _)
      }
      val trip: List[(File, AudioFileSpec, ActionArtifactLocation.QueryResult[S])] =
        tup.flatMap { case (f, spec) =>
          findLocation(f).map { loc => (f, spec, loc) }
        }

      // damn, this is annoying threading of state
      val (_, edits: List[UndoableEdit]) = ((index, List.empty[UndoableEdit]) /: trip) {
        case ((idx0, list0), (f, spec, either)) =>
          ActionArtifactLocation.merge(either).fold((idx0, list0)) { case (xs, locM) =>
            val (idx2, list2) = ((idx0, list0) /: xs) { case ((idx1, list1), x) =>
              val edit1 = EditFolderInsertObj[S]("Location", parent, idx1, x)
              (idx1 + 1, list1 :+ edit1)
            }
            val obj   = ObjectActions.mkAudioFile(locM, f, spec)
            val edit2 = EditFolderInsertObj[S]("Audio File", parent, idx2, obj)
            (idx2 + 1, list2 :+ edit2)
          }
      }
      CompoundEdit(edits, "Insert Audio Files")
    }

    private def parentOption(implicit tx: S#Tx): Option[(Folder[S], Int)] =
      treeView.dropLocation.flatMap { tdl =>
        val parentOpt = tdl.path.lastOption.fold(Option(treeView.root())) { nodeView =>
          nodeView.modelData() match {
            case objT: Folder[S]  => Some(objT)
            case _                => None
          }
        }
        parentOpt.map(_ -> tdl.index)
      }
  }
}