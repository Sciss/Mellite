/*
 *  ActionObjView.scala
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

package de.sciss.mellite.gui.impl.objview

import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.Mellite
import de.sciss.mellite.gui.impl.timeline.TimelineObjViewImpl
import de.sciss.mellite.gui.{CodeFrame, ListObjView, ObjView, TimelineObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Action, Universe}
import javax.swing.Icon

object ActionObjView extends NoArgsListObjViewFactory with TimelineObjView.Factory {
  type E[~ <: stm.Sys[~]] = Action[~] // .Elem[S]
  val icon      : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Bolt)
  val prefix    : String    = "Action"
  def humanName : String    = prefix
  def tpe       : Obj.Type  = Action
  def category  : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Action[S])(implicit tx: S#Tx): ListObjView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj = Action.Var(Action.empty[S])
    obj.name = name
    obj :: Nil
  }

  private trait Impl[S <: Sys[S]]
    extends ListObjView /* .Action */[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.NonEditable[S]
    with ListObjViewImpl.EmptyRenderer[S]
    with ActionObjView[S] {

    override def objH: stm.Source[S#Tx, Action[S]]

    override def obj(implicit tx: S#Tx): Action[S] = objH()

    final type E[~ <: stm.Sys[~]] = Action[~] // .Elem[~]

    final def factory: ObjView.Factory = ActionObjView

    final def isViewable = true

    final def openView(parent: Option[Window[S]])
                      (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      import Mellite.compiler
      val frame = CodeFrame.action(obj)
      Some(frame)
    }
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Action[S]])
    extends Impl[S]

  def mkTimelineView[S <: Sys[S]](id: S#Id, span: SpanLikeObj[S], obj: Action[S],
                                  context: TimelineObjView.Context[S])(implicit tx: S#Tx): TimelineObjView[S] = {
    val res = new TimelineImpl[S](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  private final class TimelineImpl[S <: Sys[S]](val objH : stm.Source[S#Tx, Action[S]])
    extends Impl[S] with TimelineObjViewImpl.HasMuteImpl[S]
}
trait ActionObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Action[S]
}