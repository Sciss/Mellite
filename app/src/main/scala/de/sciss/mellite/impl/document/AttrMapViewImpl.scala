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
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, Obj, Source}
import de.sciss.mellite.edit.EditAttrMap
import de.sciss.mellite.impl.MapViewImpl
import de.sciss.mellite.{AttrMapView, ObjListView, ObjView}
import de.sciss.proc.Universe

import javax.swing.undo.UndoableEdit
import scala.swing.ScrollPane

object AttrMapViewImpl {
  def apply[T <: Txn[T]](_obj: Obj[T])(implicit tx: T, universe: Universe[T],
                                       undoManager: UndoManager): AttrMapView[T] = {
    val map = _obj.attr

    val list0 = map.iterator.map {
      case (key, value) =>
        val view = ObjListView(value)
        (key, view)
    } .toIndexedSeq

    val res: AttrMapView[T] = new MapViewImpl[T, AttrMapView[T]] with AttrMapView[T] {
      protected val mapH: Source[T, Obj[T]] = tx.newHandle(_obj)

      final def obj(implicit tx: T): Obj[T] = mapH()

      val observer: Disposable[T] = _obj.attr.changed.react { implicit tx =>upd =>
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
        val edit     = EditAttrMap(name = editName, obj = obj, key = key, value = Some(value))
        Some(edit)
      }

      protected def editRenameKey(before: String, now: String, value: Obj[T])(implicit tx: T): Option[UndoableEdit] = {
        val obj0  = obj
        val ed1   = EditAttrMap(name = "Remove", obj0, key = before, value = None)
        val ed2   = EditAttrMap(name = "Insert", obj0, key = now   , value = Some(value))
        CompoundEdit(ed1 :: ed2 :: Nil, s"Rename Attribute Key")
      }

      protected def guiInit1(scroll: ScrollPane): Unit =
        component = scroll

      init(list0)
    }

    res
  }
}