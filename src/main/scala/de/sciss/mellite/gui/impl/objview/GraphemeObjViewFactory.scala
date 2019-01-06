/*
 *  GraphemeObjViewFactory.scala
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
import de.sciss.mellite.gui.{GraphemeFrame, ListObjView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Grapheme, Universe}
import javax.swing.Icon

object GraphemeObjViewFactory extends NoArgsListObjViewFactory {
  type E[S <: stm.Sys[S]] = Grapheme[S]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.LineChart)
  val prefix        : String   = "Grapheme"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = Grapheme
  def category      : String   = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Grapheme[S])(implicit tx: S#Tx): ListObjView[S] =
    new Impl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj = Grapheme[S] // .Modifiable[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Grapheme[S]])
    extends ListObjView /* .Grapheme */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.EmptyRenderer[S]
      with ListObjViewImpl.NonEditable[S] {

    type E[~ <: stm.Sys[~]] = Grapheme[~]

    def factory: ObjView.Factory = GraphemeObjViewFactory

    def isViewable = true

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = GraphemeFrame[S](objH())
      Some(frame)
    }
  }
}
