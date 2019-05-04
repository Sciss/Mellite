/*
 *  OutputObjView.scala
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

package de.sciss.mellite.gui.impl.proc

import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.{ObjListViewImpl, NoMakeListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.gui.{ObjListView, ObjView}
import de.sciss.synth.proc.Output
import javax.swing.Icon

object OutputObjView extends NoMakeListObjViewFactory {
  type E[~ <: stm.Sys[~]] = Output[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Export)
  val prefix        : String    = "Output"
  val humanName     : String    = s"Process $prefix"
  def tpe           : Obj.Type  = Output
  def category      : String    = ObjView.categMisc

  def mkListView[S <: Sys[S]](obj: Output[S])(implicit tx: S#Tx): OutputObjView[S] with ObjListView[S] = {
    val value = obj.key
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Output[S]], val value: String)
    extends OutputObjView[S]
      with ObjListView[S]
      with ObjViewImpl    .Impl[S]
      with ObjListViewImpl.StringRenderer
      with ObjViewImpl    .NonViewable[S]
      with ObjListViewImpl.NonEditable[S] {

    def factory: ObjView.Factory = OutputObjView
  }
}
trait OutputObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Output[S]
}