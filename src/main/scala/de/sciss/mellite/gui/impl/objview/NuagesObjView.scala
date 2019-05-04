/*
 *  NuagesObjView.scala
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
import de.sciss.mellite.gui.impl.document.NuagesEditorFrameImpl
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.gui.{ListObjView, ObjView}
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Timeline, Universe}
import javax.swing.Icon

object NuagesObjView extends NoArgsListObjViewFactory {
  type E[S <: stm.Sys[S]] = Nuages[S]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.CloudWhite)
  val prefix        : String   = "Nuages"
  val humanName     : String   = "Wolkenpumpe"
  def tpe           : Obj.Type  = Nuages
  def category      : String   = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Nuages[S])(implicit tx: S#Tx): ListObjView[S] =
    new Impl[S](tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val tl  = Timeline[S]
    val obj = Nuages[S](Nuages.Surface.Timeline(tl))
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Nuages[S]])
    extends ListObjView /* .Nuages */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.NonEditable[S]
      with ListObjViewImpl.EmptyRenderer[S] {

    type Repr = Nuages[S]

    def factory: ObjView.Factory = NuagesObjView

    def isViewable = true

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = NuagesEditorFrameImpl(objH())
      Some(frame)
    }
  }
}
