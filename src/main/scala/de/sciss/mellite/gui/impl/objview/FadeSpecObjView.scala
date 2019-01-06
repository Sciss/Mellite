/*
 *  FadeSpecObjView.scala
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

package de.sciss.mellite.gui.impl.objview

import de.sciss.audiowidgets.AxisFormat
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{NonViewable, raphaelIcon}
import de.sciss.mellite.gui.{ListObjView, ObjView, Shapes}
import de.sciss.synth.proc.{FadeSpec, TimeRef}
import javax.swing.Icon

import scala.swing.{Component, Label}

object FadeSpecObjView extends NoMakeListObjViewFactory {
  type E[~ <: stm.Sys[~]] = FadeSpec.Obj[~]
  val icon          : Icon      = raphaelIcon(Shapes.Aperture)
  val prefix        : String    = "FadeSpec"
  val humanName     : String    = "Fade"
  def tpe           : Obj.Type  = FadeSpec.Obj
  def category      : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: FadeSpec.Obj[S])(implicit tx: S#Tx): ListObjView[S] = {
    val value   = obj.value
    new Impl[S](tx.newHandle(obj), value).init(obj)
  }

  private val timeFmt = AxisFormat.Time(hours = false, millis = true)

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, FadeSpec.Obj[S]], var value: FadeSpec)
    extends ListObjView /* .FadeSpec */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.NonEditable[S]
      with NonViewable[S] {

    type E[~ <: stm.Sys[~]] = FadeSpec.Obj[~]

    def factory: ObjView.Factory = FadeSpecObjView

    def init(obj: FadeSpec.Obj[S])(implicit tx: S#Tx): this.type = {
      initAttrs(obj)
      disposables ::= obj.changed.react { implicit tx => upd =>
        deferAndRepaint {
          value = upd.now
        }
      }
      this
    }

    def configureRenderer(label: Label): Component = {
      val sr = TimeRef.SampleRate // 44100.0
      val dur = timeFmt.format(value.numFrames.toDouble / sr)
      label.text = s"$dur, ${value.curve}"
      label
    }
  }
}