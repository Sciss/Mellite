/*
 *  ControlObjView.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.icons.raphael
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Obj, Source, Txn => LTxn}
import de.sciss.mellite.{CodeFrame, ObjListView, ObjView}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Control, Universe}

import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object ControlObjView extends NoArgsListObjViewFactory {
  type E[~ <: LTxn[~]] = Control[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Jigsaw)
  val prefix        : String    = "Control"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Control
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Control[T])(implicit tx: T): ControlObjView[T] with ObjListView[T] = {
    val value = "" // ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    val name  = config
    val obj   = Control[T]()
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO make private
  final class Impl[T <: Txn[T]](val objH: Source[T, Control[T]], var value: String)
    extends ControlObjView[T]
      with ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.StringRenderer {

    def factory: ObjView.Factory = ControlObjView

    def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = None

    def isListCellEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = CodeFrame.control(obj)
//      val frame = ControlEditorFrame(obj)
      Some(frame)
    }
  }
}
trait ControlObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Control[T]
}