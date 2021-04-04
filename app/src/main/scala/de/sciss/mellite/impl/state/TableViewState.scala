/*
 *  TableViewState.scala
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

package de.sciss.mellite.impl.state

import de.sciss.kollflitz.Vec
import de.sciss.lucre.swing.LucreSwing.requireEDT
import de.sciss.lucre.{IntObj, IntVector, Obj, Txn}
import de.sciss.mellite.ViewState

import javax.swing.RowSorter.SortKey
import javax.swing.{JTable, SortOrder}
import javax.swing.event.{AncestorEvent, AncestorListener, ChangeEvent, ListSelectionEvent, RowSorterEvent, TableColumnModelEvent, TableColumnModelListener}
import scala.collection.JavaConverters._
import scala.swing.Table

object TableViewState {
  final val Key_ColWidths  = "col-widths"
  final val Key_RowSort    = "row-sort"
  final val Key_ColOrder   = "col-order"
}
/** Tracks view state for a Table object.
  * Tracks preferred column widths, row sorting order (single column key supported), and column order.
  */
class TableViewState[T <: Txn[T]](keyColWidths: String = TableViewState.Key_ColWidths,
                                  keyRowSort  : String = TableViewState.Key_RowSort,
                                  keyColOrder : String = TableViewState.Key_ColOrder,
                                 ) {

  private var dirtyColWidths  = false
  @volatile
  private var stateColWidths  = Vec.empty[Int]
  private var dirtyRowSort    = false
  @volatile
  private var stateRowSort    = Int.MaxValue
  private var dirtyColOrder   = false
  @volatile
  private var stateColOrder   = Vec.empty[Int]

  def entries: Set[ViewState] = {
    requireEDT()
    var res = Set.empty[ViewState]
    if (dirtyColWidths) res += ViewState(keyColWidths , IntVector , stateColWidths)
    if (dirtyRowSort  ) res += ViewState(keyRowSort   , IntObj    , stateRowSort  )
    if (dirtyColOrder ) res += ViewState(keyColOrder  , IntVector , stateColOrder )
    res
  }

  def init(tAttr: Obj.AttrMap[T])(implicit tx: T): Unit = {
    tAttr.$[IntVector](keyColWidths).foreach { v =>
      stateColWidths = v.value
    }
    tAttr.$[IntObj](keyRowSort).foreach { v =>
      stateRowSort = v.value
    }
    tAttr.$[IntVector](keyColOrder).foreach { v =>
      stateColOrder = v.value
    }
  }

  def initGUI(t: Table): Unit = initGUI_J(t.peer)

  def initGUI_J(tj: JTable): Unit = {
    val rs      = tj.getRowSorter
    val numCols = tj.getColumnCount

    if (stateColWidths.size == numCols) {
      val tcm = tj.getColumnModel
      stateColWidths.iterator.zipWithIndex.foreach { case (w, m) =>
        // note: at this point modelIndex == viewIndex
        val v = m
        tcm.getColumn(v).setPreferredWidth(w)
      }
    }

    if (stateRowSort != Int.MaxValue && rs != null) {
      val colIdx = math.abs(stateRowSort) - 1
      if (colIdx >= 0 && colIdx < numCols) {
        val order = math.signum(stateRowSort) match {
          case  1 => SortOrder.ASCENDING
          case -1 => SortOrder.DESCENDING
          case  0 => SortOrder.UNSORTED
        }
        val key = new SortKey(colIdx, order)
        rs.setSortKeys((key :: Nil).asJava)
      }
    }

    if (stateColOrder.size == numCols) {
      stateColOrder.iterator.zipWithIndex.foreach { case (m, v) =>
        val mOld = tj.convertColumnIndexToModel(v)
        if (m != mOld) {
          val vOld = tj.convertColumnIndexToView(m)
          tj.moveColumn(vOld, v)
        }
      }
    }

    // note: in model order
    def mkStateColWidths(): Vec[Int] = {
      val tcm = tj.getColumnModel
      val res = Vector.tabulate(tj.getColumnCount) { m =>
        val v = tj.convertColumnIndexToView(m)
        tcm.getColumn(v).getPreferredWidth
      }
      // println(res)
      res
    }

    def mkStateRowSort(): Int = {
      val rs = tj.getRowSorter
      val l = rs.getSortKeys
      if (l.isEmpty) 0 else {
        val k = l.get(0)
        (k.getColumn + 1) * (k.getSortOrder match {
          case SortOrder.ASCENDING  => 1
          case SortOrder.DESCENDING => -1
          case SortOrder.UNSORTED   => 0
        })
      }
    }

    def mkStateColOrder(): Vec[Int] =
      Vector.tabulate(tj.getColumnCount)(tj.convertColumnIndexToModel)

    val tcmL = new TableColumnModelListener {
      override def columnMoved(e: TableColumnModelEvent): Unit = {
        val viewFrom  = e.getFromIndex
        val viewTo    = e.getToIndex
        if (viewFrom != viewTo) {
          // val modelFrom = stateColOrder(viewFrom)
          // val modelTo = viewToModel(viewTo)
          // println(s"columnMoved: drag column $modelFrom from pos $viewFrom to $viewTo")
          stateColOrder = mkStateColOrder()
          dirtyColOrder = true
          // assert (stateColOrder(viewTo) == modelFrom)
        }
      }

      override def columnMarginChanged(e: ChangeEvent): Unit = {
        val newWidths = mkStateColWidths()
        if (stateColWidths != newWidths) {
          stateColWidths  = newWidths
          dirtyColWidths  = true
          // println(newWidths.mkString("columnMarginChanged: [", ", ", "]"))
        }
      }

      override def columnAdded            (e: TableColumnModelEvent ): Unit = ()
      override def columnRemoved          (e: TableColumnModelEvent ): Unit = ()
      override def columnSelectionChanged (e: ListSelectionEvent    ): Unit = ()
    }

    // wait because otherwise we see width changes while layout happens
    tj.addAncestorListener(new AncestorListener {
      private var init = true

      override def ancestorAdded(e: AncestorEvent): Unit = if (init) {
        init = false
        val tcm = tj.getColumnModel
        tcm.addColumnModelListener(tcmL)
      }

      override def ancestorRemoved(e: AncestorEvent): Unit = ()
      override def ancestorMoved  (e: AncestorEvent): Unit = ()
    })

    if (rs != null) rs.addRowSorterListener { e =>
      if (e.getType == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
        val newSort = mkStateRowSort()
        if (stateRowSort != newSort) {
          stateRowSort  = newSort
          dirtyRowSort  = true
          // println(s"rowSorter: $newSort")
        }
      }
    }
  }
}
