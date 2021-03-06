/*
 *  MapViewLike.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl

import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.util.EventObject

import de.sciss.desktop.{OptionPane, UndoManager, Window}
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, Obj}
import de.sciss.mellite.{DragAndDrop, MapView, ObjListView, ObjView}
import de.sciss.model.impl.ModelImpl
import de.sciss.swingplus.DropMode
import de.sciss.proc.Universe
import javax.swing.TransferHandler.TransferSupport
import javax.swing.table.{AbstractTableModel, DefaultTableCellRenderer, TableCellEditor}
import javax.swing.undo.UndoableEdit
import javax.swing.{AbstractCellEditor, JComponent, JLabel, JTable, TransferHandler, UIManager}

import scala.annotation.switch
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.TMap
import scala.swing.event.TableRowsSelected
import scala.swing.{Component, Dimension, Label, ScrollPane, Table, TextField}

abstract class MapViewImpl[T <: Txn[T], Repr]
                              (implicit val universe: Universe[T], val undoManager: UndoManager)
  extends MapView[T, Repr] with ComponentHolder[Component] with ModelImpl[MapView.Update[T, Repr]] {
  impl: Repr =>

  type C = Component

  // ---- abstract ----

  protected def observer: Disposable[T]

  protected def editImport(key: String, value: Obj[T], context: Set[ObjView.Context[T]], isInsert: Boolean)
                          (implicit tx: T): Option[UndoableEdit]

  protected def editRenameKey(before: String, now: String, value: Obj[T])(implicit tx: T): Option[UndoableEdit]

  protected def initGUI1(scroll: ScrollPane): Unit

  // ---- impl ----

  protected def showKeyOnly: Boolean = false
  protected def keyEditable: Boolean = true

  private[this] var modelEDT: Vec[(String, ObjListView[T])] = _ // = list0

  private[this] var _table: Table = _
  private[this] var _scroll: ScrollPane = _

  private[this] val viewMap = TMap.empty[String, ObjListView[T]] // TMap(list0: _*)

  final protected def table : Table      = _table
  final protected def scroll: ScrollPane = _scroll

  final protected def model: Vec[(String, ObjListView[T])] = {
    requireEDT()
    modelEDT
  }

  def dispose()(implicit tx: T): Unit = {
    import de.sciss.lucre.Txn.peer
    observer.dispose()
    viewMap.foreach(_._2.dispose())
    viewMap.clear()
  }

  // helper method that executes model updates on the EDT
  private[this] def withRow(key: String)(fun: Int => Unit)(implicit tx: T): Unit = deferTx {
    import de.sciss.equal.Implicits._
    val row = modelEDT.indexWhere(_._1 === key)
    if (row < 0) {
      warnNoView(key)
    } else {
      fun(row)
    }
  }

  private[this] def mkValueView(key: String, value: Obj[T])(implicit tx: T): ObjListView[T] = {
    val view = ObjListView(value)
    viewMap.put(key, view)(tx.peer).foreach(_.dispose())
    observeView(key, view)
    view
  }

  private def observeView(key: String, view: ObjListView[T])(implicit tx: T): Unit =
    view.react { implicit tx => {
      case ObjView.Repaint(_) =>
        withRow(key) { row =>
          tableModel.fireTableRowsUpdated(row, row)
        }

      case _ => ()
    }}

  final protected def attrAdded(key: String, value: Obj[T])(implicit tx: T): Unit = {
    val view = mkValueView(key, value)
    deferTx {
      val row = modelEDT.size
      modelEDT :+= key -> view
      tableModel.fireTableRowsInserted(row, row)
    }
  }

  final protected def attrRemoved(key: String)(implicit tx: T): Unit = {
    viewMap.remove(key)(tx.peer).foreach(_.dispose())
    withRow(key) { row =>
      modelEDT = modelEDT.patch(row, Nil, 1)
      tableModel.fireTableRowsDeleted(row, row)
    }
  }

  final protected def attrReplaced(key: String, before: Obj[T], now: Obj[T])(implicit tx: T): Unit = {
    val view = mkValueView(key, now)
    withRow(key) { row =>
      modelEDT = modelEDT.patch(row, (key -> view) :: Nil, 1)
      tableModel.fireTableRowsUpdated(row, row)
    }
  }

  private[this] def warnNoView(key: String): Unit = println(s"Warning: AttrMapView - no view found for $key")

  private[this] object tableModel extends AbstractTableModel {
    def getRowCount   : Int = modelEDT.size
    def getColumnCount: Int = if (showKeyOnly) 1 else 3

    override def getColumnName(col: Int): String = (col: @switch) match {
      case 0 => "Key"
      case 1 => "Name"
      case 2 => "Value"
    }

    def getValueAt(row /* rowView */: Int, col: Int): AnyRef = {
      // val row = tab.peer.convertRowIndexToModel(rowView)
      (col: @switch) match {
        case 0 => modelEDT(row)._1
        case 1 => modelEDT(row)._2 // .name
        case 2 => modelEDT(row)._2 // .value.asInstanceOf[AnyRef]
      }
    }

    override def getColumnClass(col: Int): Class[_] = (col: @switch) match {
      case 0 => classOf[String]
      case 1 => classOf[ObjView[T]]
      case 2 => classOf[ObjView[T]]
    }

    override def isCellEditable(row: Int, col: Int): Boolean = {
      val res = if (col == 0) keyEditable else modelEDT(row)._2.isListCellEditable
      // println(s"isCellEditable(row = $row, col = $col) -> $res")
      res
    }

    // println(s"setValueAt(value = $value, row = $row, col = $col")
    override def setValueAt(editValue: Any, row: Int, col: Int): Unit = (col: @switch) match {
      case 0 =>
        val (oldKey, view) = modelEDT(row)
        val newKey  = editValue.toString
        if (oldKey != newKey) {
          val editOpt = cursor.step { implicit tx =>
            val value = view.obj
            editRenameKey(before = oldKey, now = newKey, value = value)
          }
          editOpt.foreach(undoManager.add)
        }

      case 2 =>
        val view    = modelEDT(row)._2
        val editOpt = cursor.step { implicit tx => view.tryEditListCell(editValue) }
        editOpt.foreach(undoManager.add)

      case _ =>
    }
  }

  final protected def init(list0: Vec[(String, ObjListView[T])])(implicit tx: T): this.type = {
    import de.sciss.lucre.Txn.peer
    modelEDT  = list0
    viewMap ++= list0
    list0.foreach(tup => observeView(tup._1, tup._2))
    deferTx {
      initGUI()
    }
    this
  }

  private def initGUI(): Unit = {
    _table = new Table(tableModel) {
      // Table default has bad renderer/editor handling
      override lazy val peer: JTable = new JTable /* with Table.JTableMixin */ with SuperMixin
    }
    val jt        = _table.peer
    jt.setAutoCreateRowSorter(true)
    val tcm       = jt.getColumnModel
    val colName   = tcm.getColumn(0)
    colName .setPreferredWidth( 96)

    if (!showKeyOnly) {
      val colTpe    = tcm.getColumn(1)
      val colValue  = tcm.getColumn(2)
      colTpe  .setPreferredWidth( 96)
      colValue.setPreferredWidth(208)

      val isWebLaF = UIManager.getLookAndFeel.getID == "submin"

      colTpe.setCellRenderer(new DefaultTableCellRenderer {
        outer =>

        // trick for WebLookAndFeel (and Submin): the selection
        // rendering is done by the table UI, and this conflicts with
        // opaque labels which unfortunately come out of DefaultTableCellRenderer
        if (isWebLaF) setOpaque(false)

        private val wrap: Label = new Label { override lazy val peer: JLabel = outer }

        override def setValue(value: Any): Unit = value match {
          case view: ObjView[_] =>
            import de.sciss.equal.Implicits._
            wrap.text = if (view.name === "<unnamed>") "" else view.name
            wrap.icon = view.icon
          case _ =>
        }
      })

      colValue.setCellRenderer(new DefaultTableCellRenderer {
        outer =>

        if (isWebLaF) setOpaque(false)

        private val wrapL: Label = new Label { override lazy val peer: JLabel = outer }

        override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean,
                                                   hasFocus: Boolean, row: Int, column: Int): java.awt.Component = {
          super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column)
          if (getIcon != null) setIcon(null)
          value match {
            case view: ObjListView[_] => view.configureListCellRenderer(wrapL).peer
            case _ => outer
          }
        }
      })

      colValue.setCellEditor(new AbstractCellEditor with TableCellEditor {
        // private var currentValue: Any = null
        private val editor = new TextField(10)

        override def isCellEditable(e: EventObject): Boolean = e match {
          case m: MouseEvent => m.getClickCount >= 2
          case _ => true
        }

        def getCellEditorValue: AnyRef = editor.text // currentValue.asInstanceOf[AnyRef]

        def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, rowV: Int,
                                        col: Int): java.awt.Component = {
          val row = table.convertRowIndexToModel(rowV)
          val view      = modelEDT(row)._2
          // currentValue  = view.value
          editor.text   = view.value.toString
          editor.peer
        }
      })
    }

    jt.setPreferredScrollableViewportSize(new Dimension(if (showKeyOnly) 130 else 390, 160))

    _table.sort(0)

    jt.setDragEnabled(true)
    jt.setDropMode(DropMode.OnOrInsertRows)
    jt.setTransferHandler(new TransferHandler {
      override def getSourceActions(c: JComponent): Int = TransferHandler.LINK

      override def createTransferable(c: JComponent): Transferable = {
        val sel     = selection
        val trans1 = if (sel.size == 1) {
          val (key, value) = sel.head
          val _res = DragAndDrop.Transferable(ObjView.Flavor) {
            ObjView.Drag[T](universe, value, Set(ObjView.Context.AttrKey[T](key)))
          }
          _res
        } else null

        trans1
      }

      override def canImport(support: TransferSupport): Boolean =
        support.isDrop && {
          val dl = support.getDropLocation.asInstanceOf[JTable.DropLocation]
          val locOk = dl.isInsertRow || {
            val viewCol   = dl.getColumn
            val modelCol  = jt.convertColumnIndexToModel(viewCol)
            modelCol >= 1   // should drop on the 'type' or 'value' column
          }
          // println(s"locOk? $locOk")
          val allOk = locOk && support.isDataFlavorSupported(ObjView.Flavor)
          if (allOk) support.setDropAction(TransferHandler.LINK)
          allOk
        }

      override def importData(support: TransferSupport): Boolean =
        support.isDrop && {
          val dl        = support.getDropLocation.asInstanceOf[JTable.DropLocation]
          val isInsert  = dl.isInsertRow
          prepareImport(support, isInsert = isInsert, dropModelRow =
            if (isInsert) jt.convertRowIndexToModel(dl.getRow) else -1)
        }
    })

    _scroll = new ScrollPane(_table)
    val scrollPeer = _scroll.peer
    scrollPeer.putClientProperty("styleId", "undecorated")
    // make sure we can also drop onto the "blank" background of the scroll pane
    scrollPeer.setTransferHandler(
      new TransferHandler {
        override def canImport(support: TransferSupport): Boolean =
          support.isDrop && support.isDataFlavorSupported(ObjView.Flavor) && {
            support.setDropAction(TransferHandler.LINK)
            true
          }

        override def importData(support: TransferSupport): Boolean =
          support.isDrop && {
            prepareImport(support, isInsert = true, dropModelRow = -1)
          }
      }
    )
    _scroll.border = null
    // component     = scroll
    _table.listenTo(_table.selection)
    _table.reactions += {
      case TableRowsSelected(_, _, false) => // note: range is range of _changes_ rows, not current selection
        val sel = selection
        dispatch(MapView.SelectionChanged(impl, sel))
    }
    initGUI1(_scroll)
  }

  private def prepareImport(support: TransferSupport, isInsert: Boolean, dropModelRow: Int) = {
    val data0     = support.getTransferable.getTransferData(ObjView.Flavor).asInstanceOf[ObjView.Drag[_]]
    require(data0.universe == universe, "Cross-session list copy not yet implemented")
    val data: ObjView.Drag[T] = data0.asInstanceOf[ObjView.Drag[T]]

    val keyOpt = if (isInsert) { // ---- create new entry with key via dialog ----
      val initial = data.context.collectFirst {
        case ObjView.Context.AttrKey(k) => k
      }
      initial.fold(queryKey())(queryKey)
    } else {          // ---- update value of existing entry with key via dialog ----
      Some(modelEDT(dropModelRow)._1)
    }

    keyOpt.exists { key =>
      val editOpt = cursor.step { implicit tx =>
        editImport(isInsert = isInsert, key = key, value = data.view.obj, context = data.context)
      }
      editOpt.foreach(undoManager.add)
      editOpt.isDefined
    }
  }

  final def queryKey(initial: String): Option[String] = {
    val opt   = OptionPane.textInput(message = "Key Name", initial = initial)
    opt.title = "Create Attribute"
    opt.show(Window.find(component))
  }

  final def selection: List[(String, ObjView[T])] = {
    val ind0: List[Int] = _table.selection.rows.iterator.map(_table.peer.convertRowIndexToModel).toList
    val indices = ind0.sorted
    indices.map(modelEDT.apply)
  }
}
