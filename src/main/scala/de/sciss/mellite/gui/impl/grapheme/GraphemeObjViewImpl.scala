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

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeObjView.Factory
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.{GenericObjView, ObjViewImpl}

import scala.swing.Graphics2D

object GraphemeObjViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeID
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[S <: Sys[S]](time: LongObj[S], obj: Obj[S], mode: Mode)
                        (implicit tx: S#Tx): GraphemeObjView[S] = {
    val tid   = obj.tpe.typeID
    map.get(tid).fold(GenericObjView.mkGraphemeView(/* timed.id, */ time, obj)) { f =>
      f.mkGraphemeView(time, obj.asInstanceOf[f.E[S]], mode)
    }
  }

  private var map = Map[Int, Factory](
//    ProcObjView .tpe.typeID -> ProcObjView,
//    ActionView  .tpe.typeID -> ActionView
  )

  trait BasicImpl[S <: stm.Sys[S]] extends GraphemeObjView[S] with ObjViewImpl.Impl[S] {
    var timeValue   : Long = _
    var timeH       : stm.Source[S#Tx, LongObj[S]] = _

    // protected var idH  : stm.Source[S#Tx, S#ID] = _

    def time(implicit tx: S#Tx): LongObj[S] = timeH()

    // def id  (implicit tx: S#Tx) = idH()

    def initAttrs(/* id: S#ID, */ time: LongObj[S], obj: Obj[S])(implicit tx: S#Tx): this.type = {
      timeH         = tx.newHandle(time)
      timeValue     = time.value
      // idH           = tx.newHandle(id)
      initAttrs(obj)
    }

    def paintBack (g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()
    def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = ()
  }
}