/*
 *  WidgetObjView.scala
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

package de.sciss.mellite
package gui.impl.widget

import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Cursor, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, NoArgsListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.gui.{ListObjView, ObjView, Shapes, WidgetEditorFrame}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Universe, Widget}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object WidgetObjView extends NoArgsListObjViewFactory {
  type E[~ <: stm.Sys[~]] = Widget[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Gauge)
  val prefix        : String    = "Widget"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Widget
  def category      : String    = ObjView.categOrganisation

  def mkListView[S <: Sys[S]](obj: Widget[S])(implicit tx: S#Tx): WidgetObjView[S] with ListObjView[S] = {
    val value = "" // ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    val name  = config
    val obj   = Widget[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO make private
  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Widget[S]], var value: String)
    extends WidgetObjView[S]
      with ListObjView[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.StringRenderer {

    override def obj(implicit tx: S#Tx): Widget[S] = objH()

    def factory: ObjView.Factory = WidgetObjView

    def tryEdit(value: Any)(implicit tx: S#Tx, cursor: Cursor[S]): Option[UndoableEdit] = None

    def isEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = WidgetEditorFrame(obj)
      Some(frame)
    }
  }
}
trait WidgetObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Widget[S]
}