/*
 *  CursorsFrameImpl.scala
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

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import de.sciss.desktop
import de.sciss.desktop.Window
import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.{Cursor, Disposable, confluent}
import de.sciss.mellite.Mellite.log
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{ActionCloseAllWorkspaces, DocumentCursorsFrame, DocumentCursorsView, DocumentViewHandler, FolderFrame, GUI, Mellite, WindowPlacement}
import de.sciss.model.Change
import de.sciss.synth.proc
import de.sciss.synth.proc.{Confluent, Cursors, Durable, GenContext, Scheduler, Universe, Workspace}
import de.sciss.treetable.{AbstractTreeModel, TreeColumnModel, TreeTable, TreeTableCellRenderer, TreeTableSelectionChanged}
import javax.swing.tree.TreeNode

import scala.collection.JavaConverters.asJavaEnumerationConverter
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.Future
import scala.swing.{Action, BorderPanel, Button, Component, FlowPanel, FormattedTextField, ScrollPane}

object CursorsFrameImpl {
  type S = proc.Confluent
  type T = proc.Confluent .Txn
  type D = proc.Durable   .Txn

  def apply(workspace: Workspace.Confluent)(implicit tx: D, universe: Universe[T]): DocumentCursorsFrame = {
    val root      = workspace.cursors
    val rootView  = createView(workspace, parent = None, elem = root)
    val _view: ViewImpl = new ViewImpl(rootView)(workspace, tx.system, universe) {
      self =>
      val observer: Disposable[D] = root.changed.react { implicit tx =>upd =>
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

  private def createView(document: Workspace.Confluent, parent: Option[CursorView], elem: Cursors[T, D])
                        (implicit tx: D): CursorView = {
    import document._
    val name    = elem.name.value
    val created = confluent.Access.info(elem.seminal        ).timeStamp
    val updated = confluent.Access.info(elem.cursor.path() /* position */).timeStamp
    new CursorView(elem = elem, parent = parent, childViews = Vector.empty,
      name = name, created = created, updated = updated)
  }

  private final class CursorView(val elem: Cursors[T, D], val parent: Option[CursorView],
                                 var childViews: Vec[CursorView], var name: String,
                                 val created: Long, var updated: Long) extends TreeNode {

    def children() /*: util.Enumeration[_]*/ = childViews.iterator.asJavaEnumeration

    def getChildCount: Int = childViews.size

    def getChildAt(childIndex: Int): TreeNode = childViews(childIndex)

    def getIndex(node: TreeNode): Int = childViews.indexOf(node)

    def getParent: TreeNode = parent.orNull

    def getAllowsChildren: Boolean = true

    def isLeaf: Boolean = childViews.isEmpty
  }

  private final class FrameImpl(val view: DocumentCursorsView) // (implicit cursor: Cursor[D])
    extends WindowImpl[D]()
    with DocumentCursorsFrame {

    impl =>

//    def workspace: Workspace[T] = view.workspace

    import view.{universe, workspace}

    object WorkspaceClosed extends Disposable[T] {
      def dispose()(implicit tx: T): Unit = impl.dispose() (tx.durable)// (workspace.system.durableTx(tx))
    }

    override protected def initGUI(): Unit = {
      title       = s"${view.workspace.name} : Cursors"
      windowFile  = workspace.folder
      // missing from WindowImpl because of system mismatch
      window.reactions += {
        case desktop.Window.Activated(_) =>
          DocumentViewHandler.instance.activeDocument = Some(universe)
      }
    }

    override def dispose()(implicit tx: D): Unit = {
      // missing from WindowImpl because of system mismatch
      workspace.removeDependent(WorkspaceClosed)
      super.dispose()
    }

    override protected def performClose(): Future[Unit] = {
      log(s"Closing workspace ${workspace.folder}")
//      implicit val cursor: Cursor[T] = workspace.cursor
      ActionCloseAllWorkspaces.tryClose(workspace, Some(window))
    }

    override protected def placement: WindowPlacement = WindowPlacement(1f, 0f)
  }

  private abstract class ViewImpl(val _root: CursorView)
                                 (implicit val workspace: Workspace.Confluent, cursorD: Cursor[D],
                                  val universe: Universe[T])
    extends ComponentHolder[Component] with DocumentCursorsView {

    type Node = CursorView
    type C    = Component

    protected def observer: Disposable[D]

    private var mapViews = Map.empty[Cursors[T, D], Node]

//    final def view   = this

    final def cursor: Cursor[T] = confluent.Cursor.wrap(workspace.cursors.cursor)(workspace.system)

    def dispose()(implicit tx: D): Unit = {
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

      def getChildCount(parent: Node): Int = parent.childViews.size
      def getChild(parent: Node, index: Int): Node = parent.childViews(index)
      def isLeaf(node: Node): Boolean = node.childViews.isEmpty
      def getIndexOfChild(parent: Node, child: Node): Int = parent.childViews.indexOf(child)
      def getParent(node: Node): Option[Node] = node.parent

      def valueForPathChanged(path: TreeTable.Path[Node], newValue: Node): Unit =
        println(s"valueForPathChanged($path, $newValue)")

      def elemAdded(parent: Node, idx: Int, view: Node): Unit = {
        // if (DEBUG) println(s"model.elemAdded($parent, $idx, $view)")
        require(idx >= 0 && idx <= parent.childViews.size)
        parent.childViews = parent.childViews.patch(idx, Vector(view), 0)
        fireNodesInserted(view)
      }

      def elemRemoved(parent: Node, idx: Int): Unit = {
        // if (DEBUG) println(s"model.elemRemoved($parent, $idx)")
        require(idx >= 0 && idx < parent.childViews.size)
        val v = parent.childViews(idx)
        // this is insane. the tree UI still accesses the model based on the previous assumption
        // about the number of children, it seems. therefore, we must not update children before
        // returning from fireNodesRemoved.
        fireNodesRemoved(v)
        parent.childViews  = parent.childViews.patch(idx, Vector.empty, 1)
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
            implicit val dtx: D = tx.durable // proc.Confluent.durable(tx)
            val seminal = tx.inputAccess.takeUntil(seminalDate.getTime)
            // lucre.event.showLog = true
            parentElem.addChild(seminal)
            // lucre.event.showLog = false
          }
        case _ =>
      }
    }

    private def elemRemoved(parent: Node, idx: Int, child: Cursors[T, D])(implicit tx: D): Unit =
      mapViews.get(child).foreach { cv =>
        // NOTE: parent.children is only updated on the GUI thread through the model.
        // no way we could verify the index here!!
        //
        // val idx1 = parent.children.indexOf(cv)
        // require(idx == idx1, s"elemRemoved: given idx is $idx, but should be $idx1")
        cv.childViews.zipWithIndex.reverse.foreach { case (cc, cci) =>
          elemRemoved(cv, cci, cc.elem)
        }
        mapViews -= child
        deferTx {
          _model.elemRemoved(parent, idx)
        }
      }

    final def addChildren(parentView: Node, parent: Cursors[T, D])(implicit tx: D): Unit =
      parent.descendants.toList.zipWithIndex.foreach { case (c, ci) =>
        elemAdded(parent = parentView, idx = ci, child = c)
      }

    private def elemAdded(parent: Node, idx: Int, child: Cursors[T, D])(implicit tx: D): Unit = {
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

    final def elemUpdated(v: Node, upd: Vec[Cursors.Change[T, D]])(implicit tx: D): Unit =
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

    final def init()(implicit tx: D): Unit = deferTx(guiInit())

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
          implicit val cursor: confluent.Cursor[T, D] = confluent.Cursor.wrap(elem.cursor)(workspace.system)
          GUI.step[T]("View Elements", s"Opening root elements window for '${view.name}'") { implicit tx =>
            implicit val dtxView: Confluent.Txn => Durable.Txn = workspace.system.durableTx _ // (tx)
            implicit val dtx: Durable.Txn = dtxView(tx)
            // XXX TODO - every branch gets a fresh universe. Ok?
            implicit val universe: Universe[T] = Universe(GenContext[T](), Scheduler[T](), Mellite.auralSystem)
            val name = CellView.const[T, String](s"${workspace.name} / ${elem.name.value}")
            FolderFrame[T](name = name, isWorkspaceRoot = false)
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