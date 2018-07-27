/*
 *  CursorsFrameImpl.scala
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

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import de.sciss.desktop
import de.sciss.desktop.Window
import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm.Disposable
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.{confluent, stm}
import de.sciss.model.Change
import de.sciss.synth.proc
import de.sciss.synth.proc.{Confluent, Cursors, Durable, Workspace}
import de.sciss.treetable.{AbstractTreeModel, TreeColumnModel, TreeTable, TreeTableCellRenderer, TreeTableSelectionChanged}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future
import scala.swing.{Action, BorderPanel, Button, Component, FlowPanel, FormattedTextField, ScrollPane}

object CursorsFrameImpl {
  type S = proc.Confluent
  type D = S#D

  def apply(workspace: Workspace.Confluent)(implicit tx: D#Tx): DocumentCursorsFrame = {
    val root      = workspace.cursors
    val rootView  = createView(workspace, parent = None, elem = root)
    val _view     = new ViewImpl(rootView)(workspace, tx.system) {
      self =>
      val observer: Disposable[D#Tx] = root.changed.react { implicit tx =>upd =>
        log(s"DocumentCursorsFrame update $upd")
        self.elemUpdated(rootView, upd.changes)
      }
    }
    _view.init()
    _view.addChildren(rootView, root)
    // document.addDependent(view)

    val res = new FrameImpl(_view)
    // missing from WindowImpl because of system mismatch
    workspace.addDependent(res.WorkspaceClosed)
    res.init()
    res
  }

  private def createView(document: Workspace.Confluent, parent: Option[CursorView], elem: Cursors[S, D])
                        (implicit tx: D#Tx): CursorView = {
    import document._
    val name    = elem.name.value
    val created = confluent.Access.info(elem.seminal        ).timeStamp
    val updated = confluent.Access.info(elem.cursor.path() /* position */).timeStamp
    new CursorView(elem = elem, parent = parent, children = Vector.empty,
      name = name, created = created, updated = updated)
  }

  private final class CursorView(val elem: Cursors[S, D], val parent: Option[CursorView],
                                 var children: Vec[CursorView], var name: String,
                                 val created: Long, var updated: Long)

  private final class FrameImpl(val view: DocumentCursorsView) // (implicit cursor: stm.Cursor[D])
    extends WindowImpl[D]()
    with DocumentCursorsFrame {

    impl =>

    def workspace: Workspace[S] = view.workspace

    object WorkspaceClosed extends Disposable[S#Tx] {
      def dispose()(implicit tx: S#Tx): Unit = impl.dispose()(workspace.system.durableTx(tx))
    }

    override protected def initGUI(): Unit = {
      title       = s"${view.workspace.name} : Cursors"
      windowFile  = workspace.folder
      // missing from WindowImpl because of system mismatch
      window.reactions += {
        case desktop.Window.Activated(_) =>
          DocumentViewHandler.instance.activeDocument = Some(workspace)
      }
    }

    override def dispose()(implicit tx: D#Tx): Unit = {
      // missing from WindowImpl because of system mismatch
      workspace.removeDependent(WorkspaceClosed)
      super.dispose()
    }

    override protected def performClose(): Future[Unit] = {
      log(s"Closing workspace ${workspace.folder}")
      ActionCloseAllWorkspaces.tryClose(workspace, Some(window))
    }

    override protected def placement = WindowPlacement(1f, 0f)
  }

  private abstract class ViewImpl(val _root: CursorView)
                                 (implicit val workspace: Workspace.Confluent, cursorD: stm.Cursor[D])
    extends ComponentHolder[Component] with DocumentCursorsView {

    type Node = CursorView
    type C    = Component

    protected def observer: Disposable[D#Tx]

    private var mapViews = Map.empty[Cursors[S, D], Node]

//    final def view   = this

    final def cursor: stm.Cursor[S] = confluent.Cursor.wrap(workspace.cursors.cursor)(workspace.system)

    def dispose()(implicit tx: D#Tx): Unit = {
      // implicit val dtx = workspace.system.durableTx(tx)
      observer.dispose()
      //      // document.removeDependent(this)
      //      deferTx {
      //        window.dispose()
      //        // DocumentViewHandler.instance.remove(this)
      //      }
    }

    private class ElementTreeModel extends AbstractTreeModel[Node] {
      lazy val root: Node = _root // ! must be lazy. suckers....

      def getChildCount(parent: Node): Int = parent.children.size
      def getChild(parent: Node, index: Int): Node = parent.children(index)
      def isLeaf(node: Node): Boolean = node.children.isEmpty
      def getIndexOfChild(parent: Node, child: Node): Int = parent.children.indexOf(child)
      def getParent(node: Node): Option[Node] = node.parent

      def valueForPathChanged(path: TreeTable.Path[Node], newValue: Node): Unit =
        println(s"valueForPathChanged($path, $newValue)")

      def elemAdded(parent: Node, idx: Int, view: Node): Unit = {
        // if (DEBUG) println(s"model.elemAdded($parent, $idx, $view)")
        require(idx >= 0 && idx <= parent.children.size)
        parent.children = parent.children.patch(idx, Vector(view), 0)
        fireNodesInserted(view)
      }

      def elemRemoved(parent: Node, idx: Int): Unit = {
        // if (DEBUG) println(s"model.elemRemoved($parent, $idx)")
        require(idx >= 0 && idx < parent.children.size)
        val v = parent.children(idx)
        // this is insane. the tree UI still accesses the model based on the previous assumption
        // about the number of children, it seems. therefore, we must not update children before
        // returning from fireNodesRemoved.
        fireNodesRemoved(v)
        parent.children  = parent.children.patch(idx, Vector.empty, 1)
      }

      def elemUpdated(view: Node): Unit = fireNodesChanged(view)
    }

    private var _model: ElementTreeModel  = _
    private var t: TreeTable[Node, TreeColumnModel[Node]] = _

    private def nameAdd = "Add New Cursor"

    private def performAdd(parent: Node): Unit = {
      val format  = new SimpleDateFormat("yyyy MM dd MM | HH:mm:ss", Locale.US) // don't bother user with alpha characters
      val ggValue = new FormattedTextField(format)
      ggValue.peer.setValue(new Date(parent.updated))
      val nameOpt = GUI.keyValueDialog(value = ggValue, title = nameAdd,
        defaultName = "branch", window = Window.find(component))
      (nameOpt, ggValue.peer.getValue) match {
        case (Some(_), seminalDate: Date) =>
          val parentElem = parent.elem
          confluent.Cursor.wrap(parentElem.cursor)(workspace.system).step { implicit tx =>
            implicit val dtx: D#Tx = tx.durable // proc.Confluent.durable(tx)
            val seminal = tx.inputAccess.takeUntil(seminalDate.getTime)
            // lucre.event.showLog = true
            parentElem.addChild(seminal)
            // lucre.event.showLog = false
          }
        case _ =>
      }
    }

    private def elemRemoved(parent: Node, idx: Int, child: Cursors[S, D])(implicit tx: D#Tx): Unit =
      mapViews.get(child).foreach { cv =>
        // NOTE: parent.children is only updated on the GUI thread through the model.
        // no way we could verify the index here!!
        //
        // val idx1 = parent.children.indexOf(cv)
        // require(idx == idx1, s"elemRemoved: given idx is $idx, but should be $idx1")
        cv.children.zipWithIndex.reverse.foreach { case (cc, cci) =>
          elemRemoved(cv, cci, cc.elem)
        }
        mapViews -= child
        deferTx {
          _model.elemRemoved(parent, idx)
        }
      }

    final def addChildren(parentView: Node, parent: Cursors[S, D])(implicit tx: D#Tx): Unit =
      parent.descendants.toList.zipWithIndex.foreach { case (c, ci) =>
        elemAdded(parent = parentView, idx = ci, child = c)
      }

    private def elemAdded(parent: Node, idx: Int, child: Cursors[S, D])(implicit tx: D#Tx): Unit = {
      val cv   = createView(workspace, parent = Some(parent), elem = child)
      // NOTE: parent.children is only updated on the GUI thread through the model.
      // no way we could verify the index here!!
      //
      // val idx1 = parent.children.size
      // require(idx == idx1, s"elemAdded: given idx is $idx, but should be $idx1")
      mapViews += child -> cv
      deferTx {
        _model.elemAdded(parent, idx, cv)
      }
      addChildren(cv, child)
    }

    final def elemUpdated(v: Node, upd: Vec[Cursors.Change[S, D]])(implicit tx: D#Tx): Unit =
      upd.foreach {
        case Cursors.ChildAdded  (idx, child) => elemAdded  (v, idx, child)
        case Cursors.ChildRemoved(idx, child) => elemRemoved(v, idx, child)
        case Cursors.Renamed(Change(_, newName))  => deferTx {
          v.name = newName
          _model.elemUpdated(v)
        }
        case Cursors.ChildUpdate(Cursors.Update(source, childUpd)) => // recursion
          mapViews.get(source).foreach { cv =>
            elemUpdated(cv, childUpd)
          }
      }

    final def init()(implicit tx: D#Tx): Unit = deferTx(guiInit())

    private def guiInit(): Unit = {
      _model = new ElementTreeModel

      val colName = new TreeColumnModel.Column[Node, String]("Name") {
        def apply(node: Node): String = node.name

        def update(node: Node, value: String): Unit =
          if (value != node.name) {
            cursorD.step { implicit tx =>
              // val expr = ExprImplicits[D]
              node.elem.name_=(value)
            }
          }

        def isEditable(node: Node) = true
      }

      val colCreated = new TreeColumnModel.Column[Node, Date]("Origin") {
        def apply(node: Node): Date = new Date(node.created)
        def update(node: Node, value: Date): Unit = ()
        def isEditable(node: Node) = false
      }

      val colUpdated = new TreeColumnModel.Column[Node, Date]("Updated") {
        def apply(node: Node): Date = new Date(node.updated)
        def update(node: Node, value: Date): Unit = ()
        def isEditable(node: Node) = false
      }

      val tcm = new TreeColumnModel.Tuple3[Node, String, Date, Date](colName, colCreated, colUpdated) {
        def getParent(node: Node): Option[Node] = node.parent
      }

      t = new TreeTable[Node, TreeColumnModel[Node]](_model, tcm)
      t.showsRootHandles    = true
      t.autoCreateRowSorter = true  // XXX TODO: not sufficient for sorters. what to do?
      t.renderer = new TreeTableCellRenderer {
        private val dateFormat = new SimpleDateFormat("E d MMM yy | HH:mm:ss", Locale.US)

        private val component = TreeTableCellRenderer.Default
        def getRendererComponent(treeTable: TreeTable[_, _], value: Any, row: Int, column: Int,
                                 state: TreeTableCellRenderer.State): Component = {
          val value1 = value match {
            case d: Date  => dateFormat.format(d)
            case _        => value
          }
          val res = component.getRendererComponent(treeTable, value1, row = row, column = column, state = state)
          res // component
        }
      }
      val tabCM = t.peer.getColumnModel
      tabCM.getColumn(0).setPreferredWidth(128)
      tabCM.getColumn(1).setPreferredWidth(186)
      tabCM.getColumn(2).setPreferredWidth(186)

      val actionAdd = Action(null) {
        t.selection.paths.headOption.foreach { path =>
          val v = path.last
          performAdd(parent = v)
        }
      }
      actionAdd.enabled = false
      val ggAdd: Button = GUI.toolButton(actionAdd, raphael.Shapes.Plus, nameAdd)

      val actionDelete = Action(null) {
        println("TODO: Delete")
      }
      actionDelete.enabled = false
      val ggDelete: Button = GUI.toolButton(actionDelete, raphael.Shapes.Minus, "Delete Selected Cursor")

      val actionView = Action(null) {
        t.selection.paths.foreach { path =>
          val view  = path.last
          val elem  = view.elem
          implicit val cursor: confluent.Cursor[S, D] = confluent.Cursor.wrap(elem.cursor)(workspace.system)
          GUI.atomic[S, Unit]("View Elements", s"Opening root elements window for '${view.name}'") { implicit tx =>
            implicit val dtxView: Confluent.Txn => Durable.Txn = workspace.system.durableTx _ // (tx)
            implicit val dtx: Durable.Txn = dtxView(tx)
            // import StringObj.serializer
            //            val nameD = ExprView.expr[D, String](elem.name)
            //            val name  = nameD.map()
            val name = CellView.const[S, String](s"${workspace.name} / ${elem.name.value}")
            FolderFrame[S](name = name, isWorkspaceRoot = false)
          }
        }
      }
      actionView.enabled = false
      val ggView: Button = GUI.viewButton(actionView, "View Document At Cursor Position")

      t.listenTo(t.selection)
      t.reactions += {
        case _: TreeTableSelectionChanged[_, _] =>  // this crappy untyped event doesn't help us at all
          val selSize = t.selection.paths.size
          actionAdd .enabled  = selSize == 1
          // actionDelete.enabled  = selSize > 0
          actionView.enabled  = selSize == 1 // > 0
      }

      lazy val folderButPanel = new FlowPanel(ggAdd, ggDelete, ggView)

      val scroll    = new ScrollPane(t)
      scroll.peer.putClientProperty("styleId", "undecorated")
      scroll.border = null

      val panel = new BorderPanel {
        add(scroll,         BorderPanel.Position.Center)
        add(folderButPanel, BorderPanel.Position.South )
      }

      component = panel
    }
  }
}