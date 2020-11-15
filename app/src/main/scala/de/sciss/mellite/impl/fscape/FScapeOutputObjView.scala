/*
 *  FScapeOutputObjView.scala
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

package de.sciss.mellite.impl.fscape

import de.sciss.synth.proc.FScape
import de.sciss.icons.raphael
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.objview.{NoMakeListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.{ObjListView, ObjView}
import javax.swing.Icon

object FScapeOutputObjView extends NoMakeListObjViewFactory {
  type E[~ <: LTxn[~]] = FScape.Output[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Export)
  val prefix        : String    = "FScape.Output"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = FScape.Output
  def category      : String    = ObjView.categMisc

  private[this] lazy val _init: Unit = ObjListView.addFactory(this)

  def init(): Unit = _init

  def mkListView[T <: Txn[T]](obj: FScape.Output[T])
                             (implicit tx: T): FScapeOutputObjView[T] with ObjListView[T] = {
    val value = obj.key
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, FScape.Output[T]], val value: String)
    extends FScapeOutputObjView[T]
      with ObjListView[T]
      with ObjViewImpl    .Impl[T]
      with ObjListViewImpl.StringRenderer
      with ObjViewImpl    .NonViewable[T]
      with ObjListViewImpl.NonEditable[T] {

    override def obj(implicit tx: T): FScape.Output[T] = objH()

    def factory: ObjView.Factory = FScapeOutputObjView
  }
}
trait FScapeOutputObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = FScape.Output[T]
}