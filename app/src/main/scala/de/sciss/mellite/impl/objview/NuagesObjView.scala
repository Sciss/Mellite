/*
 *  NuagesObjView.scala
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
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.impl.document.NuagesEditorFrameImpl
import de.sciss.mellite.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Timeline, Universe}
import javax.swing.Icon

object NuagesObjView extends NoArgsListObjViewFactory {
  type E[S <: stm.Sys[T]] = Nuages[T]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.CloudWhite)
  val prefix        : String   = "Nuages"
  val humanName     : String   = "Wolkenpumpe"
  def tpe           : Obj.Type  = Nuages
  def category      : String   = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Nuages[T])(implicit tx: T): ObjListView[T] =
    new Impl[T](tx.newHandle(obj)).initAttrs(obj)

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val tl  = Timeline[T]()
    val obj = Nuages[T](Nuages.Surface.Timeline(tl))
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, Nuages[T]])
    extends ObjListView /* .Nuages */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.NonEditable[T]
      with ObjListViewImpl.EmptyRenderer[T] {

    type Repr = Nuages[T]

    def factory: ObjView.Factory = NuagesObjView

    def isViewable = true

    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = NuagesEditorFrameImpl(objH())
      Some(frame)
    }
  }
}
