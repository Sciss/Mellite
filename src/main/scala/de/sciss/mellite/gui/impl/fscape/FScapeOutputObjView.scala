/*
 *  FScapeOutputObjView.scala
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

package de.sciss.mellite.gui.impl.fscape

import de.sciss.fscape.lucre.FScape
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, NoMakeListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.gui.{ListObjView, ObjView}
import javax.swing.Icon

object FScapeOutputObjView extends NoMakeListObjViewFactory {
  type E[~ <: stm.Sys[~]] = FScape.Output[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Export)
  val prefix        : String    = "FScape.Output"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = FScape.Output
  def category      : String    = ObjView.categMisc

  private[this] lazy val _init: Unit = ListObjView.addFactory(this)

  def init(): Unit = _init

  def mkListView[S <: Sys[S]](obj: FScape.Output[S])
                             (implicit tx: S#Tx): FScapeOutputObjView[S] with ListObjView[S] = {
    val value = obj.key
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, FScape.Output[S]], val value: String)
    extends FScapeOutputObjView[S]
      with ListObjView[S]
      with ObjViewImpl    .Impl[S]
      with ListObjViewImpl.StringRenderer
      with ObjViewImpl    .NonViewable[S]
      with ListObjViewImpl.NonEditable[S] {

    override def obj(implicit tx: S#Tx): FScape.Output[S] = objH()

    def factory: ObjView.Factory = FScapeOutputObjView
  }
}
trait FScapeOutputObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): FScape.Output[S]
}