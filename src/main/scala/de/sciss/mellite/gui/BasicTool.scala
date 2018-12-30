/*
 *  BasicTool.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.gui

import java.awt.Cursor

import de.sciss.lucre.stm
import de.sciss.model.Model
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.Component

trait BasicTool[S <: stm.Sys[S], -A, +U] extends Model[U] {
  /** The mouse cursor used when the tool is active. */
  def defaultCursor: Cursor
  /** The icon to use in a tool bar. */
  def icon: Icon
  /** The human readable name of the tool. */
  def name: String

  /** Called to activate the tool to operate on the given component. */
  def install  (component: Component): Unit
  /** Called to deactivate the tool before switching to a different tool. */
  def uninstall(component: Component): Unit

  // def handleSelect(e: MouseEvent, hitTrack: Int, pos: Long, regionOpt: Option[GraphemeProcView[S]]): Unit

  /** Called after the end of a mouse drag gesture. If this constitutes a
    * valid edit, the method should return the resulting undoable edit.
    *
    * @param drag   the last editing state
    * @param cursor the cursor that might be needed to construct the undoable edit
    * @return either `Some` edit or `None` if the action does not constitute an
    *         edit or the edit parameters are invalid.
    */
  def commit(drag: A)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]
}