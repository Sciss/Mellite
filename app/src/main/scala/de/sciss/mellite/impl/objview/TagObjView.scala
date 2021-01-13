/*
 *  TagObjView.scala
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
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.proc.Implicits._
import de.sciss.proc.Tag

import javax.swing.Icon

object TagObjView extends NoArgsListObjViewFactory {
  type E[~ <: LTxn[~]] = Tag[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Tag)
  val prefix        : String    = "Tag"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Tag
  def category      : String    = ObjView.categMisc

  def mkListView[T <: Txn[T]](obj: E[T])(implicit tx: T): TagObjView[T] with ObjListView[T] = {
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)
  }

  def makeObj[T <: Txn[T]](name: Config[T])(implicit tx: T): List[Obj[T]] = {
    val obj = Tag[T]()
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, Tag[T]])
    extends TagObjView                  [T]
      with ObjListView                  [T]
      with ObjViewImpl.Impl             [T]
      with ObjViewImpl.NonViewable      [T]
      with ObjListViewImpl.NonEditable  [T]
      with ObjListViewImpl.EmptyRenderer[T] {

    def factory: ObjView.Factory = TagObjView
  }
}
trait TagObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Tag[T]
}