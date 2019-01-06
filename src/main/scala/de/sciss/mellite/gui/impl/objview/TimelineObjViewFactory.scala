/*
 *  TimelineObjViewFactory.scala
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
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.gui.{ListObjView, ObjView, TimelineFrame}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Timeline, Universe}
import javax.swing.Icon

object TimelineObjViewFactory extends NoArgsListObjViewFactory {
  type E[S <: stm.Sys[S]] = Timeline[S]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Ruler)
  val prefix        : String   = "Timeline"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = Timeline
  def category      : String   = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Timeline[S])(implicit tx: S#Tx): ListObjView[S] =
    new Impl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj = Timeline[S] // .Modifiable[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Timeline[S]])
    extends ListObjView /* .Timeline */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.EmptyRenderer[S]
      with ListObjViewImpl.NonEditable[S] {

    type E[~ <: stm.Sys[~]] = Timeline[~]

    def factory: ObjView.Factory = TimelineObjViewFactory

    def isViewable = true

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = TimelineFrame[S](objH())
      Some(frame)
    }
  }
}
