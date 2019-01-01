/*
 *  GenericObjView.scala
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

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.???!
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.impl.timeline.TimelineObjViewBasicImpl
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView, TimelineObjView}
import de.sciss.synth.proc.{Grapheme, Universe}
import javax.swing.Icon

import scala.swing.{Component, Graphics2D, Label}

object GenericObjView extends ObjView.Factory with ListObjView.Factory with GraphemeObjView.Factory {
  val icon: Icon        = ObjViewImpl.raphaelIcon(raphael.Shapes.No)
  val prefix            = "Generic"
  def humanName: String = prefix
  def tpe: Obj.Type     = ???!  // RRR
  val category          = "None"
  def hasMakeDialog     = false

  type E     [S <: stm.Sys[S]]  = Obj[S]
  type Config[S <: stm.Sys[S]]  = Unit

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = ()

  def makeObj[S <: Sys[S]](config: Unit)(implicit tx: S#Tx): List[Obj[S]] = Nil

  def mkTimelineView[S <: Sys[S]](id: S#Id, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): TimelineObjView[S] = {
    val res = new TimelineImpl[S](tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  def mkGraphemeView[S <: Sys[S]](entry: Grapheme.Entry[S], value: Obj[S], mode: Mode)
                                 (implicit tx: S#Tx): GraphemeObjView[S] = {
    val res = new GraphemeImpl[S](tx.newHandle(entry), tx.newHandle(entry.value)).initAttrs(entry)
    res
  }

  def mkListView[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx): ListObjView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  private trait Impl[S <: stm.Sys[S]] extends ObjViewImpl.Impl[S] {
    def factory: ObjView.Factory = GenericObjView

    final def value: Any = ()

    final def configureRenderer(label: Label): Component = label
  }

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Obj[S]])
    extends Impl[S] with ListObjView[S] with ListObjViewImpl.NonEditable[S] with ObjViewImpl.NonViewable[S]

  private final class TimelineImpl[S <: Sys[S]](val objH : stm.Source[S#Tx, Obj[S]])
    extends Impl[S] with TimelineObjViewBasicImpl[S] with ObjViewImpl.NonViewable[S]

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Grapheme.Entry[S]],
                                                val objH: stm.Source[S#Tx, Obj[S]])
    extends Impl[S] with GraphemeObjViewImpl.BasicImpl[S] with ObjViewImpl.NonViewable[S] {

    val insets = Insets(8, 8, 8, 8)

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = {
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
