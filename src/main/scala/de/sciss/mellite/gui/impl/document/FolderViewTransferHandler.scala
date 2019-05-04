/*
 *  FolderViewTransferHandler.scala
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

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.io.File

import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.equal.Implicits._
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Copy, Folder, Obj, Sys, Txn}
import de.sciss.lucre.swing.TreeTableView
import de.sciss.mellite.ObjectActions
import de.sciss.mellite.gui.edit.{EditFolderInsertObj, EditFolderRemoveObj}
import de.sciss.mellite.gui.{ActionArtifactLocation, DragAndDrop, FolderView, ObjListView, ObjView}
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.synth.proc.Universe
import javax.swing.TransferHandler.TransferSupport
import javax.swing.undo.UndoableEdit
import javax.swing.{JComponent, TransferHandler}

import scala.language.existentials
import scala.util.Try

/** Mixin that provides a transfer handler for the folder view. */
trait FolderViewTransferHandler[S <: Sys[S]] { fv =>
  protected def undoManager       : UndoManager
  protected implicit val universe : Universe[S]

  protected def treeView: TreeTableView[S, Obj[S], Folder[S], ObjListView[S]]
  protected def selection: FolderView.Selection[S]

  protected def findLocation(f: File): Option[ActionArtifactLocation.QueryResult[S]]

  protected object FolderTransferHandler extends TransferHandler {
    // ---- export ----

    override def getSourceActions(c: JComponent): Int =
      TransferHandler.COPY | TransferHandler.MOVE | TransferHandler.LINK // dragging only works when MOVE is included. Why?

    override def createTransferable(c: JComponent): Transferable = {
      val sel     = selection
      val trans0  = DragAndDrop.Transferable(FolderView.SelectionFlavor) {
        new FolderView.SelectionDnDData[S](fv.universe, sel)
      }
      val trans1 = if (sel.size === 1) {
        val listView = sel.head.renderData
        val tmp0  = DragAndDrop.Transferable(ObjView.Flavor) {
          new ObjView.Drag[S](fv.universe, listView)
        }
        val tmp1  = listView.createTransferable().toList
        val tmp   = trans0 :: tmp0 :: tmp1

        DragAndDrop.Transferable.seq(tmp: _*)
      } else trans0

      trans1
    }

    // ---- import ----

    override def canImport(support: TransferSupport): Boolean =
      treeView.dropLocation.exists { tdl =>
        val locOk = tdl.index >= 0 || (tdl.column === 0 && !tdl.path.lastOption.exists(_.isLeaf))
        val res = locOk && {
          if (support.isDataFlavorSupported(FolderView.SelectionFlavor)) {
            val data = support.getTransferable.getTransferData(FolderView.SelectionFlavor)
              .asInstanceOf[FolderView.SelectionDnDData[_]]
            if (data.universe != universe) {
              // no linking between sessions
              support.setDropAction(TransferHandler.COPY)
            }
            true
          } else if (support.isDataFlavorSupported(ObjView.Flavor)) {
            val data = support.getTransferable.getTransferData(ObjView.Flavor)
              .asInstanceOf[ObjView.Drag[_]]
            if (data.universe != universe) {
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
          val isList    = support.isDataFlavorSupported(ObjView.Flavor)

          // println(s"importData -- isFolder $isFolder, isList $isList")
          // println(support.getTransferable.getTransferDataFlavors.mkString("flavors: ", ", ", ""))

          val crossSessionFolder = isFolder &&
            (support.getTransferable.getTransferData(FolderView.SelectionFlavor)
              .asInstanceOf[FolderView.SelectionDnDData[_]].universe != universe)
          val crossSessionList = !crossSessionFolder && isList &&
            (support.getTransferable.getTransferData(ObjView.Flavor)
              .asInstanceOf[ObjView.Drag[_]].universe != universe)

          // println(s"importData -- crossSession ${crossSessionFolder | crossSessionList}")

          if (crossSessionFolder)
            copyFolderData(support)
          else if (crossSessionList) {
            copyListData(support)
          } else {
            import universe.cursor
            val pOpt = cursor.step { implicit tx => parentOption.map { case (f, fi) => (tx.newHandle(f), fi) }}
            // println(s"parentOption.isDefined? ${pOpt.isDefined}")
            pOpt.flatMap { case (parentH, idx) =>
              if (isFolder)
                insertFolderData(support, parentH, idx)
              else if (isList) {
                insertListData(support, parentH, idx)
              }
              else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                importFiles(support, parentH, idx)
              else None
            }
          }
        }
        editOpt.foreach(undoManager.add)
        editOpt.isDefined
      }

    // ---- folder: link ----

    private def insertFolderData(support: TransferSupport, parentH: stm.Source[S#Tx, Folder[S]],
                                 index: Int): Option[UndoableEdit] = {
      val data = support.getTransferable.getTransferData(FolderView.SelectionFlavor)
        .asInstanceOf[FolderView.SelectionDnDData[S]]

      insertFolderData1(data.selection, parentH, idx = index, dropAction = support.getDropAction)
    }

    // XXX TODO: not sure whether removal should be in exportDone or something
    private def insertFolderData1(sel0: FolderView.Selection[S], newParentH: stm.Source[S#Tx, Folder[S]], idx: Int,
                                  dropAction: Int): Option[UndoableEdit] = {
      // println(s"insert into $parent at index $idx")

      val sel = FolderView.cleanSelection(sel0)

      import de.sciss.equal.Implicits._

      val isMove = dropAction === TransferHandler.MOVE
      val isCopy = dropAction === TransferHandler.COPY

      universe.cursor.step { implicit tx =>
        val newParent = newParentH()

        def isNested(c: Obj[S]): Boolean = c match {
          case objT: Folder[S] =>
            objT === newParent || objT.iterator.toList.exists(isNested)
          case _ => false
        }

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
            import universe.cursor
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
            import universe.cursor
            EditFolderInsertObj[S](nv.renderData.humanName, newParent, idx1 + off, child = out)
          }
          context.finish()
          res
        } else {
          selZip.map { case (nv, off) =>
            import universe.cursor
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
      }} (data.universe.cursor, fv.universe.cursor)

    private def copyFolderData2[In <: Sys[In]](sel: FolderView.Selection[In], newParent: Folder[S], idx: Int)
                                       (implicit txIn: In#Tx, tx: S#Tx): Option[UndoableEdit] = {
      val idx1 = if (idx >= 0) idx else newParent.size

      val context = Copy[In, S]
      val edits = sel.zipWithIndex.map { case (nv, off) =>
        val in  = nv.modelData()
        val out = context(in)
        import universe.cursor
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

    private def insertListData(support: TransferSupport, parentH: stm.Source[S#Tx, Folder[S]],
                               index: Int): Option[UndoableEdit] = {
      val data = support.getTransferable.getTransferData(ObjView.Flavor)
        .asInstanceOf[ObjView.Drag[S]]

      val edit = universe.cursor.step { implicit tx =>
        val parent = parentH()
        insertListData1(data, parent, idx = index, dropAction = support.getDropAction)
      }
      Some(edit)
    }

    // XXX TODO: not sure whether removal should be in exportDone or something
    private def insertListData1(data: ObjView.Drag[S], parent: Folder[S], idx: Int, dropAction: Int)
                               (implicit tx: S#Tx): UndoableEdit = {
      val idx1    = if (idx >= 0) idx else parent.size
      val nv      = data.view
      val in      = nv.obj
      import universe.cursor
      val edit    = EditFolderInsertObj[S](nv.name, parent, idx1, child = in)
      edit
    }

    // ---- list: copy ----

    private def copyListData(support: TransferSupport): Option[UndoableEdit] = {
      // cf. https://stackoverflow.com/questions/20982681
      val data  = support.getTransferable.getTransferData(ObjView.Flavor)
        .asInstanceOf[ObjView.Drag[In] forSome { type In <: Sys[In] }]
      copyListData1(data)
    }

    private def copyListData1[In <: Sys[In]](data: ObjView.Drag[In]): Option[UndoableEdit] =
      Txn.copy[In, S, Option[UndoableEdit]] { (txIn: In#Tx, tx: S#Tx) =>
        parentOption(tx).map { case (parent, idx) =>
          implicit val txIn0 : In#Tx = txIn
          implicit val txOut0: S #Tx = tx
          val idx1    = if (idx >= 0) idx else parent.size
          val context = Copy[In, S]
          val nv      = data.view
          val in: Obj[In] = nv.obj
          val out     = context(in)
          import universe.cursor
          val edit    = EditFolderInsertObj[S](nv.name, parent, idx1, child = out)
          context.finish()
          edit
        }
      } (data.universe.cursor, fv.universe.cursor)

    // ---- files ----

    private def importFiles(support: TransferSupport, parentH: stm.Source[S#Tx, Folder[S]],
                            index: Int): Option[UndoableEdit] = {
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
      val (_, edits: List[UndoableEdit]) = trip.foldLeft((index, List.empty[UndoableEdit])) {
        case ((idx0, list0), (f, spec, either)) =>
          universe.cursor.step { implicit tx =>
            val parent = parentH()
            ActionArtifactLocation.merge(either).fold((idx0, list0)) { case (xs, locM) =>
              import universe.cursor
              val (idx2, list2) = xs.foldLeft((idx0, list0)) { case ((idx1, list1), x) =>
                val edit1 = EditFolderInsertObj[S]("Location", parent, idx1, x)
                (idx1 + 1, list1 :+ edit1)
              }
              val obj   = ObjectActions.mkAudioFile(locM, f, spec)
              val edit2 = EditFolderInsertObj[S]("Audio File", parent, idx2, obj)
              (idx2 + 1, list2 :+ edit2)
            }
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