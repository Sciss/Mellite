/*
 *  TimelineObjViewFactory.scala
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
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.{ObjListView, ObjView, TimelineFrame}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Timeline, Universe}
import javax.swing.Icon

object TimelineObjView extends NoArgsListObjViewFactory {
  type E[T <: LTxn[T]] = Timeline[T]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Ruler)
  val prefix        : String    = "Timeline"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Timeline
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Timeline[T])(implicit tx: T): ObjListView[T] =
    new Impl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj = Timeline[T]() // .Modifiable[T]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  trait Basic[T <: Txn[T]]
    extends ObjViewImpl.Impl[T]
      with TimelineObjView[T] {

    def factory: ObjView.Factory = TimelineObjView

    def isViewable = true

    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = TimelineFrame[T](objH())
      Some(frame)
    }
  }

  private final class Impl[T <: Txn[T]](val objH: Source[T, Timeline[T]])
    extends Basic[T]
      with ObjListViewImpl.EmptyRenderer[T]
      with ObjListViewImpl.NonEditable[T] {
  }
}
trait TimelineObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Timeline[T]
}