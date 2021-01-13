/*
 *  ObjListView.scala
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

package de.sciss.mellite

import de.sciss.lucre.{Cursor, Obj, Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.objview.ObjListViewImpl
import javax.swing.undo.UndoableEdit

import scala.swing.{Component, Label}

object ObjListView {
  trait Factory extends ObjView.Factory {
    def mkListView[T <: Txn[T]](obj: E[T])(implicit tx: T): ObjListView[T]
  }

  def addFactory(f: Factory): Unit = ObjListViewImpl.addFactory(f)

  def factories: Iterable[Factory] = ObjListViewImpl.factories

  def apply[T <: Txn[T]](obj: Obj[T])(implicit tx: T): ObjListView[T] = ObjListViewImpl(obj)
}
trait ObjListView[T <: LTxn[T]] extends ObjView[T] {
  /** The opaque view value passed into the renderer. */
  def value: Any

  def nameOption_=(value: Option[String]): Unit

  /** Configures the value cell renderer. The simplest case would be
    * `label.text = value.toString`. In order to leave the cell blank, just return the label.
    * One can also set its icon.
    */
  def configureListCellRenderer(label: Label): Component

  /** Whether the opaque value part of the view can be edited in-place (inside the table itself). */
  def isListCellEditable: Boolean

  /** Given that the view is editable, this method is called when the editor gave notification about
    * the editing being done. It is then the duty of the view to issue a corresponding transactional
    * mutation, returned in an undoable edit. Views that do not support editing should just return `None`.
    */
  def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit]
}