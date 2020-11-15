/*
 *  GraphemeObjView.scala
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
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.GraphemeFrame
import de.sciss.proc.Implicits._
import de.sciss.proc.{Grapheme, Universe}
import javax.swing.Icon

object GraphemeObjView extends NoArgsListObjViewFactory {
  type E[T <: LTxn[T]] = Grapheme[T]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.LineChart)
  val prefix        : String    = "Grapheme"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Grapheme
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Grapheme[T])(implicit tx: T): ObjListView[T] =
    new Impl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj = Grapheme[T]() // .Modifiable[T]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, Grapheme[T]])
    extends ObjListView /* .Grapheme */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.EmptyRenderer[T]
      with ObjListViewImpl.NonEditable[T]
      with GraphemeObjView[T] {

    def factory: ObjView.Factory = GraphemeObjView

    def isViewable = true

    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = GraphemeFrame[T](objH())
      Some(frame)
    }
  }
}
trait GraphemeObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Grapheme[T]
}