/*
 *  FolderViewImpl.scala
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

import java.io.File

import de.sciss.desktop.UndoManager
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.expr.{CellView, StringObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Folder, Obj}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.TreeTableView
import de.sciss.lucre.swing.TreeTableView.ModelUpdate
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ActionArtifactLocation, ArtifactLocationObjView, FolderView, ObjListView}
import de.sciss.mellite.FolderView.Selection
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.model.impl.ModelImpl
import de.sciss.serial.Serializer
import de.sciss.synth.proc.{ObjKeys, Universe}
import de.sciss.treetable.j.{DefaultTreeTableCellEditor, TreeTableCellEditor}
import de.sciss.treetable.{TreeTableCellRenderer, TreeTableSelectionChanged}
import javax.swing.event.{CellEditorListener, ChangeEvent}
import javax.swing.undo.UndoableEdit
import javax.swing.{CellEditor, DropMode}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component
import scala.util.control.NonFatal

object FolderViewImpl extends FolderView.Companion {
  def install(): Unit =
    FolderView.peer = this

  def apply[T <: Txn[T]](root0: Folder[T])
                        (implicit tx: T, universe: Universe[T], undoManager: UndoManager): FolderView[T] = {
    implicit val folderSer: Serializer[T, S#Acc, Folder[T]] = Folder.serializer[T]

    new Impl[T] {
      val treeView: TreeTableView[T, Obj[T], Folder[T], ObjListView[T]] = TreeTableView(root0, TTHandler)

      deferTx {
        guiInit()
      }
    }
  }

  def cleanSelection[S <: stm.Sys[T]](in: Selection[T]): Selection[T] = {
    type NodeView = FolderView.NodeView[T]
    type Sel      = Selection[T]

    @tailrec
    def loop(set: Set[NodeView], rem: Sel, res: Sel): Sel = rem match {
      case Nil => res
      case head :: tail =>
        head.parentView match {
          case Some(p) if set.contains(p) => loop(set = set       , rem = tail, res =         res)
          case _                          => loop(set = set + head, rem = tail, res = head :: res)
        }
    }

    @tailrec
    def countParents(n: NodeView, res: Int = 0): Int = n.parentView match {
      case None     => res
      case Some(p)  => countParents(p, res = res + 1)
    }

    val inS = in.sortBy(countParents(_))
    val resRev = loop(Set.empty, rem = inS, res = Nil)
    resRev.reverse
  }

  private abstract class Impl[T <: Txn[T]](implicit val undoManager: UndoManager, val universe: Universe[T])
    extends ComponentHolder[Component]
    with FolderView[T]
    with ModelImpl[FolderView.Update[T]]
    with FolderViewTransferHandler[T] {

    view =>

    type C = Component

    private type Data     = ObjListView[T]
    private type NodeView = FolderView.NodeView[T]

    protected object TTHandler
      extends TreeTableView.Handler[T, Obj[T], Folder[T], ObjListView[T]] {

      def branchOption(node: Obj[T]): Option[Folder[T]] = node match {
        case fe: Folder[T] => Some(fe)
        case _ => None
      }

      def children(branch: Folder[T])(implicit tx: T): Iterator[Obj[T]] =
        branch.iterator

      private def updateObjectName(obj: Obj[T], nameOption: Option[String])(implicit tx: T): Boolean = {
        treeView.nodeView(obj).exists { nv =>
          val objView = nv.renderData
          deferTx {
            objView.nameOption = nameOption
          }
          true
        }
      }

      private type MUpdate = ModelUpdate[Obj[T], Folder[T]]

      // XXX TODO - d'oh this because ugly
      def observe(obj: Obj[T], dispatch: T => MUpdate => Unit)
                 (implicit tx: T): Disposable[T] = {
        val objH      = tx.newHandle(obj)
        val objReact  = obj.changed.react { implicit tx => _ =>
          // theoretically, we don't need to refresh the object,
          // because `treeView` uses an id-map for lookup.
          // however, there might be a problem with objects
          // created in the same transaction as the call to `observe`.
          //
          val obj = objH()
          val isDirty = treeView.nodeView(obj).exists { _ =>
            // val objView = nv.renderData
            false // XXX TODO RRR ELEM objView.isUpdateVisible(u1)
          }
          if (isDirty) dispatch(tx)(TreeTableView.NodeChanged(obj): MUpdate)
        }
        val attr      = obj.attr
        val nameView  = CellView.attr[T, String, StringObj](attr, ObjKeys.attrName)
        val attrReact = nameView.react { implicit tx => nameOpt =>
          val isDirty = updateObjectName(obj, nameOpt)
          if (isDirty) dispatch(tx)(TreeTableView.NodeChanged(obj): MUpdate)
        }

        val folderReact = obj match {
          case f: Folder[T] =>
            val res = f.changed.react { implicit tx => u2 =>
              u2.list.modifiableOption.foreach { folder =>
                val m = updateBranch(folder, u2.changes)
                m.foreach(dispatch(tx)(_))
              }
            }
            Some(res)

          case _ => None
        }

        new Disposable[T] {
          def dispose()(implicit tx: T): Unit = {
            objReact .dispose()
            attrReact.dispose()
            folderReact.foreach(_.dispose())
          }
        }
      }

      private def updateBranch(parent: Folder[T], changes: Vec[Folder.Change[T]]): Vec[MUpdate] =
        changes.flatMap {
          case Folder.Added  (idx, obj) => Vec(TreeTableView.NodeAdded  (parent, idx, obj): MUpdate)
          case Folder.Removed(idx, obj) => Vec(TreeTableView.NodeRemoved(parent, idx, obj): MUpdate)
        }

//      private lazy val isWebLaF = UIManager.getLookAndFeel.getID == "submin"

      private lazy val component = {
        val res = TreeTableCellRenderer.Default
        // XXX TODO: somehow has no effect?
//        res.peer.putClientProperty("styleId", "renderer")
        res
      }

      def renderer(tt: TreeTableView[T, Obj[T], Folder[T], Data], node: NodeView, row: Int, column: Int,
                   state: TreeTableCellRenderer.State): Component = {
        val data    = node.renderData
        val isFirst = column == 0
        val value1  = if (isFirst) data.name else "" // data.value
        // val value1  = if (value != {}) value else null
        // XXX TODO --- a bit ugly work-around for web-laf renderer
        // val state1 = if (!isFirst /*isWebLaF*/ && state.selected) state.copy(selected = false) else state
        val res = component.getRendererComponent(tt.treeTable, value1, row = row, column = column, state = state)
        if (isFirst) {
          if (row >= 0 && node.isLeaf) {
            try {
              // val node = t.getNode(row)
              component.icon = data.icon
            } catch {
              case NonFatal(_) => // XXX TODO -- currently NPE problems; seems renderer is called before tree expansion with node missing
            }
          }
          res // component
        } else {
          // XXX TODO --- this doesn't work yet
//          if (isWebLaF) component.opaque = false
          data.configureListCellRenderer(component)
        }
      }

      private var editView    = Option.empty[ObjListView[T]]
      private var editColumn  = 0

      private lazy val defaultEditorJ = new javax.swing.JTextField
      private lazy val defaultEditor: TreeTableCellEditor = {
        val res = new DefaultTreeTableCellEditor(defaultEditorJ)
        res.addCellEditorListener(new CellEditorListener {
          def editingCanceled(e: ChangeEvent): Unit = ()
          def editingStopped (e: ChangeEvent): Unit = editView.foreach { objView =>
            editView = None
            val editOpt: Option[UndoableEdit] = cursor.step { implicit tx =>
              val text = defaultEditorJ.getText
              if (editColumn == 0) {
                val valueOpt: Option[StringObj[T]] /* Obj[T] */ = if (text.isEmpty || text.toLowerCase == "<unnamed>") None else {
                  val expr = StringObj.newConst[T](text)
                  Some(expr)
                }
                val ed = EditAttrMap.expr[T, String, StringObj](s"Rename ${objView.humanName} Element", objView.obj, ObjKeys.attrName,
                  valueOpt)
                Some(ed)
              } else {
                objView.tryEditListCell(text)
              }
            }
            editOpt.foreach(undoManager.add)
          }
        })
        res
      }
      private lazy val defaultEditorC = Component.wrap(defaultEditorJ)

      def isEditable(data: Data, column: Int): Boolean = column == 0 || data.isListCellEditable

      val columnNames: Vec[String] = Vector("Name", "Value")

      def editor(tt: TreeTableView[T, Obj[T], Folder[T], Data], node: NodeView, row: Int, column: Int,
                 selected: Boolean): (Component, CellEditor) = {
        val data    = node.renderData
        editView    = Some(data)
        editColumn  = column
        val value   = if (column == 0) data.name else data.value.toString
        defaultEditor.getTreeTableCellEditorComponent(tt.treeTable.peer, value, selected, row, column)
        (defaultEditorC, defaultEditor)
      }

      def data(node: Obj[T])(implicit tx: T): Data = ObjListView(node)
    }

    protected def treeView: TreeTableView[T, Obj[T], Folder[T], ObjListView[T]]

    def dispose()(implicit tx: T): Unit = {
      treeView.dispose()
    }

    def root: Source[T, Folder[T]] = treeView.root

    protected def guiInit(): Unit = {
      val t = treeView.treeTable
      t.rootVisible = false
      t.rowHeight   = 22  // XXX TODO : times font scale

      val tabCM = t.peer.getColumnModel
      tabCM.getColumn(0).setPreferredWidth(176)
      tabCM.getColumn(1).setPreferredWidth(272)

      t.listenTo(t.selection)
      t.reactions += {
        case _: TreeTableSelectionChanged[_, _] =>  // this crappy untyped event doesn't help us at all
          // println(s"selection: $e")
          dispatch(FolderView.SelectionChanged(view, selection))
        // case e => println(s"other: $e")
      }
      t.showsRootHandles  = true
      // t.expandPath(TreeTable.Path(_model.root))
      t.dragEnabled       = true
      t.dropMode          = DropMode.ON_OR_INSERT_ROWS
      t.peer.setTransferHandler(FolderTransferHandler)
      val tc        = treeView.component
//      tc.peer.putClientProperty("styleId", "nofocus")
      tc.peer.putClientProperty("styleId", "undecorated")
      component     = tc

    }

    def selection: Selection[T] = treeView.selection

    def insertionPoint(implicit tx: T): (Folder[T], Int) = treeView.insertionPoint

    def locations: Vec[ArtifactLocationObjView[T]] = selection.iterator.flatMap { nodeView =>
      nodeView.renderData match {
        case view: ArtifactLocationObjView[T] => Some(view)
        case _ => None
      }
    } .toIndexedSeq

    def findLocation(f: File): Option[ActionArtifactLocation.QueryResult[T]] = {
      val locationsOk = locations.flatMap { view =>
        try {
          val dir = view.directory
          Artifact.relativize(dir, f)
          Some((Left(view.objH), dir))
        } catch {
          case NonFatal(_) => None
        }
      } .headOption

      locationsOk.orElse {
        ActionArtifactLocation.query[T](file = f /*, folder = parent */)(implicit tx => treeView.root()) // , window = Some(comp))
      }
    }
  }
}