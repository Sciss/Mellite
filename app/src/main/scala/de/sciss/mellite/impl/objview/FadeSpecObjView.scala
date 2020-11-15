/*
 *  FadeSpecObjView.scala
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

import de.sciss.audiowidgets.AxisFormat
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.impl.objview.ObjViewImpl.{NonViewable, raphaelIcon}
import de.sciss.mellite.Shapes
import de.sciss.proc.{FadeSpec, TimeRef}
import javax.swing.Icon

import scala.swing.{Component, Label}

object FadeSpecObjView extends NoMakeListObjViewFactory {
  type E[~ <: LTxn[~]] = FadeSpec.Obj[~]
  val icon          : Icon      = raphaelIcon(Shapes.Aperture)
  val prefix        : String    = "FadeSpec"
  val humanName     : String    = "Fade"
  def tpe           : Obj.Type  = FadeSpec.Obj
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: FadeSpec.Obj[T])(implicit tx: T): ObjListView[T] = {
    val value   = obj.value
    new Impl[T](tx.newHandle(obj), value).init(obj)
  }

  private val timeFmt = AxisFormat.Time(hours = false, millis = true)

  final class Impl[T <: Txn[T]](val objH: Source[T, FadeSpec.Obj[T]], var value: FadeSpec)
    extends ObjListView /* .FadeSpec */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.NonEditable[T]
      with NonViewable[T] {

    type Repr = FadeSpec.Obj[T]

    def factory: ObjView.Factory = FadeSpecObjView

    def init(obj: FadeSpec.Obj[T])(implicit tx: T): this.type = {
      initAttrs(obj)
      addDisposable(obj.changed.react { implicit tx =>upd =>
        deferAndRepaint {
          value = upd.now
        }
      })
      this
    }

    def configureListCellRenderer(label: Label): Component = {
      val sr = TimeRef.SampleRate // 44100.0
      val dur = timeFmt.format(value.numFrames.toDouble / sr)
      label.text = s"$dur, ${value.curve}"
      label
    }
  }
}
