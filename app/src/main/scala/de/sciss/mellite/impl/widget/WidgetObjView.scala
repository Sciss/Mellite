/*
 *  WidgetObjView.scala
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

package de.sciss.mellite.impl.widget

import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.objview.{NoArgsListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.{ObjListView, ObjView, Shapes, WidgetEditorFrame}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Universe, Widget}

import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object WidgetObjView extends NoArgsListObjViewFactory {
  type E[~ <: LTxn[~]] = Widget[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Gauge)
  val prefix        : String    = "Widget"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Widget
  def category      : String    = ObjView.categOrganization

  def mkListView[T <: Txn[T]](obj: Widget[T])(implicit tx: T): WidgetObjView[T] with ObjListView[T] = {
    val value = "" // ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    val name  = config
    val obj   = Widget[T]()
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO make private
  final class Impl[T <: Txn[T]](val objH: Source[T, Widget[T]], var value: String)
    extends WidgetObjView[T]
      with ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.StringRenderer {

    def factory: ObjView.Factory = WidgetObjView

    def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = None

    def isListCellEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = WidgetEditorFrame(obj)
      Some(frame)
    }
  }
}
trait WidgetObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Widget[T]
}