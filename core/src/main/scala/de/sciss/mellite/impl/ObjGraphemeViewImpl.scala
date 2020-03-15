/*
 *  GraphemeObjViewImpl.scala
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

package de.sciss.mellite.impl

import de.sciss.lucre.expr.{Expr, LongObj}
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.GraphemeView.Mode
import de.sciss.mellite.ObjGraphemeView.Factory
import de.sciss.mellite.impl.objview.{GenericObjView, ObjViewImpl}
import de.sciss.mellite.{GraphemeRendering, GraphemeView, ObjGraphemeView}
import de.sciss.synth.proc.Grapheme
import de.sciss.synth.proc.Grapheme.Entry

import scala.swing.Graphics2D

object ObjGraphemeViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeId
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[S <: Sys[S]](entry: Grapheme.Entry[S], mode: Mode)
                        (implicit tx: S#Tx): ObjGraphemeView[S] = {
    val tid = entry.value.tpe.typeId
    map.get(tid).fold(GenericObjView.mkGraphemeView(entry = entry, value = entry.value, mode = mode)) { f =>
      f.mkGraphemeView(entry = entry, value = entry.value.asInstanceOf[f.E[S]], mode = mode)
    }
  }

  private var map = Map.empty[Int, Factory]

  trait BasicImpl[S <: stm.Sys[S]] extends ObjGraphemeView[S] with ObjViewImpl.Impl[S] {
    final var timeValue: Long = _

//    final var succ = Option.empty[GraphemeObjView[S]]

    def succ_=(opt: Option[ObjGraphemeView[S]])(implicit tx: S#Tx): Unit = ()

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

  trait SimpleExpr[S <: Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]]
    extends BasicImpl[S] with ObjViewImpl.SimpleExpr[S, A, Ex] {

    def init(ex: Ex[S], entry: Entry[S])(implicit tx: S#Tx): this.type = {
      init(ex)
      initAttrs(entry)
      this
    }
  }
}