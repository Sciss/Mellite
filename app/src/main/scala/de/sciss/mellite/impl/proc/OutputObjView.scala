/*
 *  OutputObjView.scala
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

package de.sciss.mellite.impl.proc

import de.sciss.icons.raphael
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.objview.{NoMakeListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.synth.proc.Proc
import javax.swing.Icon

object OutputObjView extends NoMakeListObjViewFactory {
  type E[~ <: LTxn[~]] = Proc.Output[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Export)
  val prefix        : String    = "Output"
  val humanName     : String    = s"Process $prefix"
  def tpe           : Obj.Type  = Proc.Output
  def category      : String    = ObjView.categMisc

  def mkListView[T <: Txn[T]](obj: Proc.Output[T])(implicit tx: T): OutputObjView[T] with ObjListView[T] = {
    val value = obj.key
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, Proc.Output[T]], val value: String)
    extends OutputObjView[T]
      with ObjListView[T]
      with ObjViewImpl    .Impl[T]
      with ObjListViewImpl.StringRenderer
      with ObjViewImpl    .NonViewable[T]
      with ObjListViewImpl.NonEditable[T] {

    def factory: ObjView.Factory = OutputObjView
  }
}
trait OutputObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Proc.Output[T]
}