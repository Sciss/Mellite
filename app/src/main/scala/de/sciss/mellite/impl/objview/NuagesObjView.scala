/*
 *  NuagesObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.objview

import de.sciss.icons.raphael
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.document.NuagesEditorFrameImpl
import de.sciss.mellite.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.nuages.Nuages
import de.sciss.proc.Implicits._
import de.sciss.proc.{Timeline, Universe}

import javax.swing.Icon

object NuagesObjView extends NoArgsListObjViewFactory {
  type E[T <: LTxn[T]] = Nuages[T]
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
    if (name.nonEmpty) obj.name = name
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
