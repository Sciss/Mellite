/*
 *  GenericObjView.scala
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

import de.sciss.icons.raphael
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Ident, Obj, Source, SpanLikeObj, Txn => LTxn}
import de.sciss.mellite.GraphemeView.Mode
import de.sciss.mellite.impl.ObjGraphemeViewImpl
import de.sciss.mellite.impl.timeline.ObjTimelineViewBasicImpl
import de.sciss.mellite.{GraphemeRendering, GraphemeView, Insets, ObjGraphemeView, ObjListView, ObjTimelineView, ObjView}
import de.sciss.proc.{Grapheme, Universe}

import javax.swing.Icon
import scala.swing.{Component, Graphics2D, Label}

object GenericObjView extends NoMakeListObjViewFactory with ObjGraphemeView.Factory {
  val icon: Icon        = ObjViewImpl.raphaelIcon(raphael.Shapes.No)
  val prefix            = "Generic"
  def humanName: String = prefix
  def tpe: Obj.Type     = ???  // RRR
  val category          = "None"

  type E[T <: LTxn[T]]  = Obj[T]

  def mkTimelineView[T <: Txn[T]](id: Ident[T], span: SpanLikeObj[T], obj: Obj[T])
                                 (implicit tx: T): ObjTimelineView[T] = {
    val peer  = ObjListView(obj)
    val res   = new TimelineImpl[T](peer).initAttrs(id, span, obj)
    res
  }

  def mkGraphemeView[T <: Txn[T]](entry: Grapheme.Entry[T], value: Obj[T], mode: Mode)
                                 (implicit tx: T): ObjGraphemeView[T] = {
    val res = new GraphemeImpl[T](tx.newHandle(entry), tx.newHandle(entry.value)).initAttrs(entry)
    res
  }

  def mkListView[T <: Txn[T]](obj: Obj[T])(implicit tx: T): ObjListView[T] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  private trait Impl[T <: LTxn[T]] extends ObjViewImpl.Impl[T] {

    type Repr = Obj[T]

    def factory: ObjView.Factory = GenericObjView
  }

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, Obj[T]])
    extends Impl[T] with ObjListView[T] with ObjListViewImpl.NonEditable[T] with ObjViewImpl.NonViewable[T] {

    def value: Any = ()

    def configureListCellRenderer(label: Label): Component = label
  }

  private final class TimelineImpl[T <: Txn[T]](peer: ObjView[T])
    extends Impl[T] with ObjTimelineViewBasicImpl[T] {

    def objH: Source[T, Obj[T]] = peer.objH

    def isViewable: Boolean = peer.isViewable

    def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] =
      peer.openView(parent)
  }

  private final class GraphemeImpl[T <: Txn[T]](val entryH: Source[T, Grapheme.Entry[T]],
                                                val objH: Source[T, Obj[T]])
    extends Impl[T] with ObjGraphemeViewImpl.BasicImpl[T] with ObjViewImpl.NonViewable[T] {

    val insets = Insets(8, 8, 8, 8)

    override def paintFront(g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit = {
      val c   = gv.canvas
      val jc  = c.canvasComponent.peer
      val h   = jc.getHeight
      val x   = c.frameToScreen(timeValue)
      val y   = h/2
      val p   = r.shape1
      p.reset()
      raphael.Shapes.No(p)
      val at  = r.transform1
      at.setToTranslation(x - 8, y - 8)
//      at.setToScale(0.5, 0.5)
//      at.translate(x - 8, y - 8)
      at.scale(0.5, 0.5)
      val pt  = at.createTransformedShape(p)
      val selected = gv.selectionModel.contains(this)
      g.setPaint(if (selected) r.pntRegionBackgroundSelected else r.pntRegionBackground)
      g.fill(pt)
    }
  }
}
