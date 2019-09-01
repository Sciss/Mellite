/*
 *  ControlObjView.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Cursor, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{CodeFrame, ObjListView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Control, Universe}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object ControlObjView extends NoArgsListObjViewFactory {
  type E[~ <: stm.Sys[~]] = Control[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Jigsaw)
  val prefix        : String    = "Control"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Control
  def category      : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Control[S])(implicit tx: S#Tx): ControlObjView[S] with ObjListView[S] = {
    val value = "" // ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    val name  = config
    val obj   = Control[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO make private
  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Control[S]], var value: String)
    extends ControlObjView[S]
      with ObjListView[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.StringRenderer {

    def factory: ObjView.Factory = ControlObjView

    def tryEditListCell(value: Any)(implicit tx: S#Tx, cursor: Cursor[S]): Option[UndoableEdit] = None

    def isListCellEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = CodeFrame.control(obj)
//      val frame = ControlEditorFrame(obj)
      Some(frame)
    }
  }
}
trait ControlObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Control[S]
}