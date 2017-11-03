/*
 *  GraphemeObjViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl.grapheme

import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeObjView.Factory
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.{GenericObjView, ObjViewImpl}
import de.sciss.synth.proc.Grapheme

import scala.swing.Graphics2D

object GraphemeObjViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeID
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[S <: Sys[S]](entry: Grapheme.Entry[S], numFrames: Long, mode: Mode)
                        (implicit tx: S#Tx): GraphemeObjView[S] = {
    val tid = entry.value.tpe.typeID
    map.get(tid).fold(GenericObjView.mkGraphemeView(entry = entry, numFrames = numFrames, mode = mode)) { f =>
      f.mkGraphemeView(entry = entry, numFrames = numFrames, mode = mode)
    }
  }

  private var map = Map[Int, Factory](
//    ProcObjView .tpe.typeID -> ProcObjView,
//    ActionView  .tpe.typeID -> ActionView
  )

  trait BasicImpl[S <: stm.Sys[S]] extends GraphemeObjView[S] with ObjViewImpl.Impl[S] {
    var timeValue: Long = _

    def initAttrs(entry: Grapheme.Entry[S])(implicit tx: S#Tx): this.type = {
      val time      = entry.key
      timeValue     = time.value
      initAttrs(obj)
    }

    def paintBack (g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()
    def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()
  }
}