/*
 *  ActionObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.objview

import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{CodeFrame, Mellite, ObjListView, ObjTimelineView, ObjView}
import de.sciss.mellite.impl.ObjTimelineViewImpl
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{ActionRaw, Universe}
import javax.swing.Icon

@deprecated("Action should be used instead of ActionRaw", since = "2.46.1")
object ActionRawObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: stm.Sys[~]] = ActionRaw[~] // .Elem[S]
  val icon      : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.No)
  val prefix    : String    = "ActionRaw"
  def humanName : String    = s"$prefix (obsolete)"
  def tpe       : Obj.Type  = ActionRaw
  def category  : String    = ObjView.categMisc // categComposition

  def mkListView[S <: Sys[S]](obj: ActionRaw[S])(implicit tx: S#Tx): ObjListView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj = ActionRaw.Var(ActionRaw.empty[S])
    obj.name = name
    obj :: Nil
  }

  private trait Impl[S <: Sys[S]]
    extends ObjListView /* .Action */[S]
    with ObjViewImpl.Impl[S]
    with ObjListViewImpl.NonEditable[S]
    with ObjListViewImpl.EmptyRenderer[S]
    with ActionRawObjView[S] {

    override def objH: stm.Source[S#Tx, ActionRaw[S]]

    override def obj(implicit tx: S#Tx): ActionRaw[S] = objH()

    final type E[~ <: stm.Sys[~]] = ActionRaw[~] // .Elem[~]

    final def factory: ObjView.Factory = ActionRawObjView

    final def isViewable = true

    final def openView(parent: Option[Window[S]])
                      (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      import Mellite.compiler
      val frame = CodeFrame.actionRaw(obj)
      Some(frame)
    }
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, ActionRaw[S]])
    extends Impl[S]

  def mkTimelineView[S <: Sys[S]](id: S#Id, span: SpanLikeObj[S], obj: ActionRaw[S],
                                  context: ObjTimelineView.Context[S])(implicit tx: S#Tx): ObjTimelineView[S] = {
    val res = new TimelineImpl[S](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  private final class TimelineImpl[S <: Sys[S]](val objH : stm.Source[S#Tx, ActionRaw[S]])
    extends Impl[S] with ObjTimelineViewImpl.HasMuteImpl[S]
}
trait ActionRawObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = ActionRaw[S]
}