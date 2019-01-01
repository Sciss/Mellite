/*
 *  GraphemeObjViewImpl.scala
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

package de.sciss.mellite
package gui
package impl.grapheme

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeObjView.Factory
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.objview.{DoubleObjView, DoubleVectorObjView, EnvSegmentObjView, GenericObjView, ObjViewImpl}
import de.sciss.synth.proc.Grapheme

import scala.swing.Graphics2D

object GraphemeObjViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeId
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[S <: Sys[S]](entry: Grapheme.Entry[S], mode: Mode)
                        (implicit tx: S#Tx): GraphemeObjView[S] = {
    val tid = entry.value.tpe.typeId
    map.get(tid).fold(GenericObjView.mkGraphemeView(entry = entry, value = entry.value, mode = mode)) { f =>
      f.mkGraphemeView(entry = entry, value = entry.value.asInstanceOf[f.E[S]], mode = mode)
    }
  }

  private var map = Map[Int, Factory](
    DoubleObjView       .tpe.typeId -> DoubleObjView,
    DoubleVectorObjView .tpe.typeId -> DoubleVectorObjView,
    EnvSegmentObjView   .tpe.typeId -> EnvSegmentObjView
//    ProcObjView .tpe.typeId -> ProcObjView,
//    ActionView  .tpe.typeId -> ActionView
  )

  trait BasicImpl[S <: stm.Sys[S]] extends GraphemeObjView[S] with ObjViewImpl.Impl[S] {
    final var timeValue: Long = _

//    final var succ = Option.empty[GraphemeObjView[S]]

    def succ_=(opt: Option[GraphemeObjView[S]])(implicit tx: S#Tx): Unit = ()

    final def entry(implicit tx: S#Tx): Grapheme.Entry[S] = entryH()

    final def time(implicit tx: S#Tx): LongObj[S] = entry.key

    def initAttrs(entry: Grapheme.Entry[S])(implicit tx: S#Tx): this.type = {
      val time      = entry.key
      timeValue     = time.value
      initAttrs(obj)
    }

    def paintBack (g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()
    def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()
  }
}