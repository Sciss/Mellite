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

import de.sciss.lucre.{Expr, LongObj}
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.GraphemeView.Mode
import de.sciss.mellite.ObjGraphemeView.Factory
import de.sciss.mellite.impl.objview.{GenericObjView, ObjViewImpl}
import de.sciss.mellite.{GraphemeRendering, GraphemeView, ObjGraphemeView}
import de.sciss.proc.Grapheme
import de.sciss.proc.Grapheme.Entry

import scala.swing.Graphics2D

object ObjGraphemeViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeId
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[T <: Txn[T]](entry: Grapheme.Entry[T], mode: Mode)
                        (implicit tx: T): ObjGraphemeView[T] = {
    val tid = entry.value.tpe.typeId
    map.get(tid).fold(GenericObjView.mkGraphemeView(entry = entry, value = entry.value, mode = mode)) { f =>
      f.mkGraphemeView(entry = entry, value = entry.value.asInstanceOf[f.E[T]], mode = mode)
    }
  }

  private var map = Map.empty[Int, Factory]

  trait BasicImpl[T <: LTxn[T]] extends ObjGraphemeView[T] with ObjViewImpl.Impl[T] {
    final var timeValue: Long = _

//    final var succ = Option.empty[GraphemeObjView[T]]

    def succ_=(opt: Option[ObjGraphemeView[T]])(implicit tx: T): Unit = ()

    final def entry(implicit tx: T): Grapheme.Entry[T] = entryH()

    final def time(implicit tx: T): LongObj[T] = entry.key

    def initAttrs(entry: Grapheme.Entry[T])(implicit tx: T): this.type = {
      val time      = entry.key
      timeValue     = time.value
      initAttrs(obj)
    }

    def paintBack (g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit = ()
    def paintFront(g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit = ()
  }

  trait SimpleExpr[T <: Txn[T], A, Ex[~ <: LTxn[~]] <: Expr[~, A]]
    extends BasicImpl[T] with ObjViewImpl.SimpleExpr[T, A, Ex] {

    def init(ex: Ex[T], entry: Entry[T])(implicit tx: T): this.type = {
      init(ex)
      initAttrs(entry)
      this
    }
  }
}