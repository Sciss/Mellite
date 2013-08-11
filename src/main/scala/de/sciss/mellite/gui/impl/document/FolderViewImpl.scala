/*
 *  FolderViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package document

import de.sciss.synth.proc.{Artifact, Sys}
import swing.{ScrollPane, Component}
import scala.collection.{JavaConversions, breakOut}
import collection.immutable.{IndexedSeq => Vec}
import scalaswingcontrib.tree.Tree
import de.sciss.lucre.stm.{Disposable, Cursor, IdentifierMap}
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.expr.ExprImplicits
import scala.util.control.NonFatal
import de.sciss.lucre.event.Change
import javax.swing.{DropMode, JComponent, TransferHandler}
import java.awt.datatransfer.{DataFlavor, Transferable}
import javax.swing.TransferHandler.TransferSupport
import de.sciss.treetable.{TreeTableSelectionChanged, TreeTableCellRenderer, TreeColumnModel, AbstractTreeModel, TreeTable}
import de.sciss.treetable.TreeTableCellRenderer.TreeState
import java.io.File
import de.sciss.lucre.stm
import de.sciss.synth.io.AudioFile
import scala.util.Try
import de.sciss.mellite.gui.impl.timeline.TimelineDnD

object FolderViewImpl {
  private final val DEBUG = false

  def apply[S <: Sys[S]](document: Document[S], root: Folder[S])
                        (implicit tx: S#Tx, cursor: Cursor[S]): FolderView[S] = {
    val _doc    = document
    val _cursor = cursor
    new Impl[S] {
      val cursor    = _cursor
      val mapViews  = tx.newInMemoryIDMap[ElementView[S]]  // folder IDs to renderers
      val rootView  = ElementView.Root(root)
      val document  = _doc

      private def buildMapView(f: Folder[S], fv: ElementView.FolderLike[S]): Unit = {
        val tup = f.iterator.map(c => c -> ElementView(fv, c)(tx)).toIndexedSeq
        fv.children = tup.map(_._2)
        tup.foreach { case (c, cv) =>
          mapViews.put(c.id, cv)
          (c, cv) match {
            case (cf: Element.Folder[S], cfv: ElementView.Folder[S]) =>
              buildMapView(cf.entity, cfv)
            case _ =>
          }
        }
      }
      buildMapView(root, rootView)

      val observer = root.changed.react { implicit tx => upd =>
        val c = rootView.convert(upd)
        folderUpdated(rootView, c)
      }

      guiFromTx {
        guiInit()
      }
    }
  }

  private abstract class Impl[S <: Sys[S]]
    extends ComponentHolder[Component] with FolderView[S] with ModelImpl[FolderView.Update[S]] {
    view =>

    type Node   = ElementView.Renderer[S]
    type Branch = ElementView.FolderLike[S]
    type Path   = TreeTable.Path[Branch]

    protected def rootView: ElementView.Root[S]
    protected def mapViews: IdentifierMap[S#ID, S#Tx, ElementView[S]]
    protected implicit def cursor: Cursor[S]
    protected def observer: Disposable[S#Tx]
    protected def document: Document[S]

    private class ElementTreeModel extends AbstractTreeModel[Node] {
      lazy val root: Node = rootView // ! must be lazy. suckers....

      def getChildCount(parent: Node): Int = parent match {
        case b: ElementView.FolderLike[S] => b.children.size
        case _ => 0
      }

      def getChild(parent: Node, index: Int): ElementView[S] = parent match {
        case b: ElementView.FolderLike[S] => b.children(index)
        case _ => sys.error(s"parent $parent is not a branch")
      }

      def isLeaf(node: Node): Boolean = node match {
        case b: ElementView.FolderLike[S] => false // b.children.nonEmpty
        case _ => true
      }

      def getIndexOfChild(parent: Node, child: Node): Int = parent match {
        case b: ElementView.FolderLike[S] => b.children.indexOf(child)
        case _ => sys.error(s"parent $parent is not a branch")
      }

      def getParent(node: Node): Option[Node] = node.parent

      def valueForPathChanged(path: TreeTable.Path[Node], newValue: Node): Unit =
        println(s"valueForPathChanged($path, $newValue)")

      def elemAdded(parent: ElementView.FolderLike[S], idx: Int, view: ElementView[S]): Unit = {
        if (DEBUG) println(s"model.elemAdded($parent, $idx, $view)")
        val g       = parent  // Option.getOrElse(_root)
        require(idx >= 0 && idx <= g.children.size)
        g.children  = g.children.patch(idx, Vector(view), 0)
        fireNodesInserted(view)
      }

      def elemRemoved(parent: ElementView.FolderLike[S], idx: Int): Unit = {
        if (DEBUG) println(s"model.elemRemoved($parent, $idx)")
        require(idx >= 0 && idx < parent.children.size)
        val v       = parent.children(idx)
        // this is frickin insane. the tree UI still accesses the model based on the previous assumption
        // about the number of children, it seems. therefore, we must not update children before
        // returning from fireNodesRemoved.
        fireNodesRemoved(v)
        parent.children  = parent.children.patch(idx, Vector.empty, 1)
      }

      def elemUpdated(view: ElementView[S]): Unit = {
        if (DEBUG) println(s"model.elemUpdated($view)")
        fireNodesChanged(view)
      }
    }

    private var _model: ElementTreeModel  = _
    private var t: TreeTable[Node, TreeColumnModel[Node]] = _

    def elemAdded(parent: ElementView.FolderLike[S], idx: Int, elem: Element[S])(implicit tx: S#Tx): Unit = {
      if (DEBUG) println(s"elemAdded($parent, $idx $elem)")
      val v = ElementView(parent, elem)
      mapViews.put(elem.id, v)

      guiFromTx {
        _model.elemAdded(parent, idx, v)
      }

      (elem, v) match {
        case (f: Element.Folder[S], fv: ElementView.Folder[S]) =>
          val fe    = f.entity
          // val path  = parent :+ gv
          // branchAdded(path, gv)
          if (!fe.isEmpty) {
            fe.iterator.toList.zipWithIndex.foreach { case (c, ci) =>
              elemAdded(fv, ci, c)
            }
          }

        case _ =>
      }
    }

    def elemRemoved(parent: ElementView.FolderLike[S], idx: Int, elem: Element[S])(implicit tx: S#Tx): Unit = {
      if (DEBUG) println(s"elemRemoved($parent, $idx, $elem)")
      mapViews.get(elem.id).foreach { v =>
        (elem, v) match {
          case (f: Element.Folder[S], fv: ElementView.Folder[S]) =>
            // val path = parent :+ gl
            val fe = f.entity
            if (fe.nonEmpty) fe.iterator.toList.zipWithIndex.reverse.foreach { case (c, ci) =>
              elemRemoved(fv, ci, c)
            }

          case _ =>
        }

        mapViews.remove(elem.id)

        guiFromTx {
          _model.elemRemoved(parent, idx)
        }
      }
    }

    def elemUpdated(elem: Element[S], changes: Vec[Element.Change[S]])(implicit tx: S#Tx): Unit = {
      val viewOpt = mapViews.get(elem.id)
      if (viewOpt.isEmpty) {
        println(s"WARNING: No view for elem $elem")
      }
      viewOpt.foreach { v =>
        changes.foreach {
          case Element.Renamed(Change(_, newName)) =>
            guiFromTx {
              v.name = newName
              _model.elemUpdated(v)
            }

          case Element.Entity(ch) =>
            v match {
              case fv: ElementView.Folder[S] =>
                val upd = fv.tryConvert(ch)
                if (upd.isEmpty) println(s"WARNING: unhandled $elem -> $ch")
                folderUpdated(fv, upd)

              case _ =>
                if (v.checkUpdate(ch)) guiFromTx(_model.elemUpdated(v))
            }
        }
      }
    }

    def folderUpdated(fv: ElementView.FolderLike[S], upd: Folder.Update[S])(implicit tx: S#Tx): Unit =
      upd.foreach {
        case Folder.Added  (idx, elem)      => elemAdded  (fv, idx, elem)
        case Folder.Removed(idx, elem)      => elemRemoved(fv, idx, elem)
        case Folder.Element(elem, elemUpd)  => elemUpdated(elem, elemUpd.changes)
      }

    def dispose()(implicit tx: S#Tx): Unit = {
      observer.dispose()
      mapViews.dispose()
    }

    protected def guiInit(): Unit = {
      requireEDT()
      require(comp == null, "Initialization called twice")

      _model = new ElementTreeModel

      val colName = new TreeColumnModel.Column[Node, String]("Name") {
        def apply(node: Node): String = node.name

        def update(node: Node, value: String): Unit =
          node match {
            case v: ElementView[S] if value != v.name =>
              cursor.step { implicit tx =>
                val expr = ExprImplicits[S]
                import expr._
                v.element().name() = value
              }
            case _ =>
          }

        def isEditable(node: Node) = node match {
          case b: ElementView[S] => true
          case _ => false // i.e. Root
        }
      }

      val colValue = new TreeColumnModel.Column[Node, Any]("Value") {
        def apply(node: Node): Any = node.value

        def update(node: Node, value: Any): Unit =
          cursor.step { implicit tx => node.tryUpdate(value) }

        def isEditable(node: Node) = node match {
          case b: ElementView.FolderLike[S] => false
          case _ => true
        }
      }

      val tcm = new TreeColumnModel.Tuple2[Node, String, Any](colName, colValue) {
        def getParent(node: Node): Option[Node] = node.parent
      }

      t = new TreeTable(_model, tcm)
      t.rootVisible = false
      t.renderer    = new TreeTableCellRenderer {
        private val component = TreeTableCellRenderer.Default
        def getRendererComponent(treeTable: TreeTable[_, _], value: Any, row: Int, column: Int,
                                 state: TreeTableCellRenderer.State): Component = {
          val value1 = if (value != ()) value else null
          val res = component.getRendererComponent(treeTable, value1, row = row, column = column, state = state)
          if (row >= 0) state.tree match {
            case Some(TreeState(false, true)) =>
              // println(s"row = $row, col = $column")
              try {
                val node = t.getNode(row)
                component.icon = node.icon
              } catch {
                case NonFatal(_) => // XXX TODO -- currently NPE probs; seems renderer is called before tree expansion with node missing
              }
            case _ =>
          }
          res // component
        }
      }
      val tabCM = t.peer.getColumnModel
      tabCM.getColumn(0).setPreferredWidth(176)
      tabCM.getColumn(1).setPreferredWidth(256)

      t.listenTo(t.selection)
      t.reactions += {
        case e: TreeTableSelectionChanged[_, _] =>  // this crappy untyped event doesn't help us at all
          // println(s"selection: $e")
          dispatch(FolderView.SelectionChanged(view, selection))
        // case e => println(s"other: $e")
      }
      t.showsRootHandles  = true
      t.expandPath(Tree.Path(_model.root))
      t.dragEnabled       = true
      t.dropMode          = DropMode.ON_OR_INSERT_ROWS
      t.peer.setTransferHandler(new TransferHandler {
        // ---- export ----

        override def getSourceActions(c: JComponent): Int = {
          TransferHandler.COPY | TransferHandler.MOVE // dragging only works when MOVE is included. Why?
        }
        override def createTransferable(c: JComponent): Transferable = {
          val sel   = selection
          val tSel  = DragAndDrop.Transferable(FolderView.selectionFlavor) {
            new FolderView.SelectionDnDData(document, selection)
          }
          sel.headOption match {
            case Some((_, elemView: ElementView.Int[S])) =>
              val elem = elemView.element
              val tElem = DragAndDrop.Transferable(TimelineDnD.flavor) {
                TimelineDnD.IntDrag[S](document, elem)
              }
              DragAndDrop.Transferable.seq(tSel, tElem)

            case _ => tSel
          }
        }

        // ---- import ----
        override def canImport(support: TransferSupport): Boolean = {
          t.dropLocation match {
            case Some(tdl) =>
              // println(s"last = ${tdl.path.last}; column ${tdl.column}; isLeaf? ${t.treeModel.isLeaf(tdl.path.last)}")
              val locOk = tdl.index >= 0 || (tdl.column == 0 && (tdl.path.last match {
                case _: ElementView.Folder[_] => true
                case _                        => false
              }))

              if (locOk) {
                // println("Supported flavours:")
                // support.getDataFlavors.foreach(println)

                // println(s"File? ${support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)}")
                // println(s"Action = ${support.getUserDropAction}")

                support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                  (support.isDataFlavorSupported(FolderView.selectionFlavor) &&
                   support.getUserDropAction == TransferHandler.MOVE)

              } else {
                false
              }

            case _ => false
          }
        }

        // XXX TODO: not sure whether removal should be in exportDone or something
        private def insertData(sel: FolderView.Selection[S], newParentView: Branch, idx: Int): Boolean = {
          // println(s"insert into $parent at index $idx")

          def isNested(pv: Branch, cv: Branch): Boolean =
            pv == cv || pv.children.collect {
              case pcv: ElementView.Folder[S] => pcv
            }.exists(isNested(_, cv))

          // make sure we are not moving a folder within itself (it will magically disappear :)
          val sel1 = sel.filter {
            case (_, cv: ElementView.Folder[S]) if isNested(cv, newParentView) => false
            case _ => true
          }

          // if we move children within the same folder, adjust the insertion index by
          // decrementing it for any child which is above the insertion index, because
          // we will first remove all children, then re-insert them.
          val idx0 = if (idx >= 0) idx else newParentView.children.size
          val idx1 = idx0 - sel1.count {
            case (_ :+ `newParentView`, cv) => newParentView.children.indexOf(cv) <= idx0
            case _ => false
          }
          // println(s"idx0 $idx0 idx1 $idx1")

          cursor.step { implicit tx =>
            val tup = sel1.map {
              case (_ :+ pv, cv) => pv.folder -> cv.element()
            }

            val newParent = newParentView.folder
            tup             .foreach { case  (oldParent, c)       => oldParent.remove(            c) }
            tup.zipWithIndex.foreach { case ((_        , c), off) => newParent.insert(idx1 + off, c) }
          }

          true
        }

        private def importSelection(support: TransferSupport, parent: ElementView.FolderLike[S], index: Int): Boolean = {
          val data = support.getTransferable.getTransferData(FolderView.selectionFlavor)
            .asInstanceOf[FolderView.SelectionDnDData[S]]
          if (data.document == document) {
            val sel = data.selection
            insertData(sel, parent, index)
          } else {
            false
          }
        }

        private def importFiles(support: TransferSupport, parent: ElementView.FolderLike[S], index: Int): Boolean = {
          import JavaConversions._
          val data  = support.getTransferable.getTransferData(DataFlavor.javaFileListFlavor)
            .asInstanceOf[java.util.List[File]].toList
          val tup   = data.flatMap { f =>
            Try(AudioFile.readSpec(f)).toOption.map(f -> _)
          }
          val trip  = tup.flatMap { case (f, spec) =>
            findLocation(f).map { loc => (f, spec, loc) }
          }

          if (trip.nonEmpty) {
            cursor.step { implicit tx =>
              trip.foreach {
                case (f, spec, locS) =>
                  val loc = locS()
                  loc.entity.modifiableOption.foreach { locM =>
                    ElementActions.addAudioFile(parent.folder, index, locM, f, spec)
                  }
              }
            }
            true

          } else {
            false
          }
        }

        override def importData(support: TransferSupport): Boolean =
          t.dropLocation match {
            case Some(tdl) =>
              tdl.path.last match {
                case parent: ElementView.FolderLike[S] =>
                  val idx = tdl.index
                  if      (support.isDataFlavorSupported(FolderView.selectionFlavor   ))
                    importSelection(support, parent, idx)
                  else if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                    importFiles    (support, parent, idx)
                  else false

                case _ => false
              }

            case _ => false
          }
      })

      val scroll    = new ScrollPane(t)
      scroll.border = null
      comp          = scroll
    }

    def selection: FolderView.Selection[S] =
      t.selection.paths.collect({
        case PathExtrator(path, child) => (path, child)
      })(breakOut)

    def locations: Vec[ElementView.ArtifactLocation[S]] = selection.collect {
      case (_, view: ElementView.ArtifactLocation[S]) => view
    }

    def findLocation(f: File): Option[stm.Source[S#Tx, Element.ArtifactLocation[S]]] = {
      val locsOk = locations.flatMap { view =>
        try {
          Artifact.relativize(view.directory, f)
          Some(view)
        } catch {
          case NonFatal(_) => None
        }
      } .headOption

      locsOk match {
        case Some(loc)  => Some(loc.element)
        case _          =>
          val parent = selection.collect {
            case (_, f: ElementView.Folder[S]) => f.element
          } .headOption
          ActionArtifactLocation.query(document, file = f, folder = parent) // , window = Some(comp))
      }
    }

    object PathExtrator {
      def unapply(path: Seq[Node]): Option[(Vec[ElementView.FolderLike[S]], ElementView[S])] =
        path match {
          case init :+ (last: ElementView[S]) =>
            val pre: Vec[ElementView.FolderLike[S]] = init.map({
              case g: ElementView.FolderLike[S] => g
              case _ => return None
            })(breakOut)
            Some((/* _root +: */ pre, last))
          case _ => None
        }
    }
  }
}