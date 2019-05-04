/*
 *  ListObjView.scala
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

package de.sciss.mellite.gui

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.impl.objview.ListObjViewImpl
import javax.swing.undo.UndoableEdit

import scala.swing.{Component, Label}

object ListObjView {
  trait Factory extends ObjView.Factory {
    def mkListView[S <: SSys[S]](obj: E[S])(implicit tx: S#Tx): ListObjView[S]
  }

  def addFactory(f: Factory): Unit = ListObjViewImpl.addFactory(f)

  def factories: Iterable[Factory] = ListObjViewImpl.factories

  def apply[S <: SSys[S]](obj: Obj[S])(implicit tx: S#Tx): ListObjView[S] = ListObjViewImpl(obj)
}
trait ListObjView[S <: stm.Sys[S]] extends ObjView[S] {
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
  def tryEditListCell(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]
}