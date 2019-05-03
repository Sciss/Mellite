/*
 *  AttrMapViewImpl.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.mellite.gui.impl.MapViewImpl
import de.sciss.mellite.gui.{AttrMapView, ListObjView}
import de.sciss.synth.proc.Universe
import javax.swing.undo.UndoableEdit

import scala.swing.ScrollPane

object AttrMapViewImpl {
  def apply[S <: Sys[S]](_obj: Obj[S])(implicit tx: S#Tx, universe: Universe[S],
                                       undoManager: UndoManager): AttrMapView[S] = {
    val map = _obj.attr

    val list0 = map.iterator.map {
      case (key, value) =>
        val view = ListObjView(value)
        (key, view)
    } .toIndexedSeq

    val res: AttrMapView[S] = new MapViewImpl[S, AttrMapView[S]] with AttrMapView[S] {
      protected val mapH: stm.Source[S#Tx, Obj[S]] = tx.newHandle(_obj)

      final def obj(implicit tx: S#Tx): Obj[S] = mapH()

      val observer: Disposable[S#Tx] = _obj.attr.changed.react { implicit tx =>upd =>
        upd.changes.foreach {
          case Obj.AttrAdded   (key, value)       => attrAdded   (key, value)
          case Obj.AttrRemoved (key, _    )       => attrRemoved (key)
          case Obj.AttrReplaced(key, before, now) => attrReplaced(key, before = before, now = now)
          case _ =>
        }
      }

      protected def editImport(key: String, value: Obj[S], isInsert: Boolean)(implicit tx: S#Tx): Option[UndoableEdit] = {
        val editName = if (isInsert) s"Create Attribute '$key'" else s"Change Attribute '$key'"
        val edit     = EditAttrMap(name = editName, obj = obj, key = key, value = Some(value))
        Some(edit)
      }

      protected def editRenameKey(before: String, now: String, value: Obj[S])(implicit tx: S#Tx): Option[UndoableEdit] = {
        val obj0  = obj
        val ed1   = EditAttrMap(name = "Remove", obj0, key = before, value = None)
        val ed2   = EditAttrMap(name = "Insert", obj0, key = now   , value = Some(value))
        CompoundEdit(ed1 :: ed2 :: Nil, s"Rename Attribute Key")
      }

      protected def guiInit1(scroll: ScrollPane): Unit = component = scroll

      init(list0)
    }

    res
  }
}