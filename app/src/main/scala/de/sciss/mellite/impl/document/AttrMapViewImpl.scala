/*
 *  AttrMapViewImpl.scala
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

package de.sciss.mellite.impl.document

import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.kollflitz.Vec
import de.sciss.lucre.swing.LucreSwing.requireEDT
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, IntObj, IntVector, Obj, Source}
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.impl.{MapViewImpl, WindowImpl}
import de.sciss.mellite.{AttrMapView, ObjListView, ObjView, ViewState}
import de.sciss.proc.{Tag, Universe}

import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.event.{AncestorEvent, AncestorListener, ChangeEvent, ListSelectionEvent, RowSorterEvent, TableColumnModelEvent, TableColumnModelListener}
import javax.swing.undo.UndoableEdit
import scala.collection.JavaConverters._
import scala.swing.ScrollPane

object AttrMapViewImpl {
  final val StateKey_ColWidths  = "col-widths"
  final val StateKey_RowSort    = "row-sort"
  final val StateKey_ColOrder   = "col-order"

  def apply[T <: Txn[T]](_receiver: Obj[T])(implicit tx: T, universe: Universe[T],
                                            undoManager: UndoManager): AttrMapView[T] = {
    val map = _receiver.attr

    val list0 = map.iterator.map {
      case (key, value) =>
        val view = ObjListView(value)
        (key, view)
    } .toIndexedSeq

    val res: AttrMapView[T] = new MapViewImpl[T, AttrMapView[T]] with AttrMapView[T] {
      private val receiverH: Source[T, Obj[T]] = tx.newHandle(_receiver)

      final def obj(implicit tx: T): Obj.AttrMap[T] = receiverH().attr

      override def receiver(implicit tx: T): Obj[T] = receiverH()

      private var dirtyColWidths  = false
      @volatile
      private var stateColWidths  = Vec.empty[Int] // mkStateColWidths()
      private var dirtyRowSort    = false
      @volatile
      private var stateRowSort    = Int.MaxValue
      private var dirtyColOrder   = false
      @volatile
      private var stateColOrder   = Vec.empty[Int] // mkStateColOrder ()

      override def viewState: Set[ViewState] = {
        requireEDT()
        var res = Set.empty[ViewState]
        if (dirtyColWidths) res += ViewState(StateKey_ColWidths , IntVector , stateColWidths)
        if (dirtyRowSort  ) res += ViewState(StateKey_RowSort   , IntObj    , stateRowSort  )
        if (dirtyColOrder ) res += ViewState(StateKey_ColOrder  , IntVector , stateColOrder )
        res
      }

      override protected val observer: Disposable[T] = _receiver.attr.changed.react { implicit tx =>upd =>
        upd.changes.foreach {
          case Obj.AttrAdded   (key, value)       => attrAdded   (key, value)
          case Obj.AttrRemoved (key, _    )       => attrRemoved (key)
          case Obj.AttrReplaced(key, before, now) => attrReplaced(key, before = before, now = now)
          case _ =>
        }
      }

      protected def editImport(key: String, value: Obj[T], context: Set[ObjView.Context[T]], isInsert: Boolean)
                              (implicit tx: T): Option[UndoableEdit] = {
        val editName = if (isInsert) s"Create Attribute '$key'" else s"Change Attribute '$key'"
        val edit     = EditAttrMap(name = editName, obj = receiver, key = key, value = Some(value))
        Some(edit)
      }

      protected def editRenameKey(before: String, now: String, value: Obj[T])(implicit tx: T): Option[UndoableEdit] = {
        val obj0  = receiver
        val ed1   = EditAttrMap(name = "Remove", obj0, key = before, value = None)
        val ed2   = EditAttrMap(name = "Insert", obj0, key = now   , value = Some(value))
        CompoundEdit(ed1 :: ed2 :: Nil, s"Rename Attribute Key")
      }

      // note: in model order
      private def mkStateColWidths(): Vec[Int] = {
        val t   = table.peer
        val tcm = t.getColumnModel
        val res = Vector.tabulate(t.getColumnCount) { m =>
          val v = t.convertColumnIndexToView(m)
          tcm.getColumn(v).getPreferredWidth
        }
        // println(res)
        res
      }

      private def mkStateRowSort(): Int = {
        val rs = table.peer.getRowSorter
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

      private def mkStateColOrder(): Vec[Int] = {
        val t = table.peer
        Vector.tabulate(t.getColumnCount)(t.convertColumnIndexToModel)
      }

      protected def guiInit1(scroll: ScrollPane): Unit = {
        val t       = table.peer
        val tcm     = t.getColumnModel
        val rs      = t.getRowSorter
        val numCols = t.getColumnCount

        if (stateColWidths.size == numCols) {
          stateColWidths.iterator.zipWithIndex.foreach { case (w, m) =>
            // note: at this point modelIndex == viewIndex
            val v = m
            tcm.getColumn(v).setPreferredWidth(w)
          }
        }

        if (stateRowSort != Int.MaxValue) {
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
            val mOld = t.convertColumnIndexToModel(v)
            if (m != mOld) {
              val vOld = t.convertColumnIndexToView(m)
              t.moveColumn(vOld, v)
            }
          }
        }

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
        t.addAncestorListener(new AncestorListener {
          private var init = true

          override def ancestorAdded(e: AncestorEvent): Unit = if (init) {
            init = false
            // println("Table shown")
            tcm.addColumnModelListener(tcmL)
          }

          override def ancestorRemoved(e: AncestorEvent): Unit = ()
          override def ancestorMoved  (e: AncestorEvent): Unit = ()
        })

        rs.addRowSorterListener { e =>
          if (e.getType == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
            val newSort = mkStateRowSort()
            if (stateRowSort != newSort) {
              stateRowSort  = newSort
              dirtyRowSort  = true
              // println(s"rowSorter: $newSort")
            }
          }
        }

        component = scroll
      }

      {
        init(list0)
        for {
          attr  <- tx.attrMapOption(map)
          tag   <- attr.$[Tag](WindowImpl.StateKey_Base)
          tAttr <- tx.attrMapOption(tag)
        } {
          tAttr.$[IntVector](StateKey_ColWidths).foreach { v =>
            stateColWidths = v.value
          }
          tAttr.$[IntObj](StateKey_RowSort).foreach { v =>
            stateRowSort = v.value
          }
          tAttr.$[IntVector](StateKey_ColOrder).foreach { v =>
            stateColOrder = v.value
          }
        }
      }
    }

    res
  }
}