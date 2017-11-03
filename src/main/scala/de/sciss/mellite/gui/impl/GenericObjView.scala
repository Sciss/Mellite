/*
 *  GenericObjView.scala
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
package impl

import javax.swing.Icon

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Cursor, Obj}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.impl.timeline.TimelineObjViewBasicImpl
import de.sciss.synth.proc.{Grapheme, Workspace}

import scala.swing.{Component, Label}

object GenericObjView extends ObjView.Factory {
  val icon: Icon        = ObjViewImpl.raphaelIcon(raphael.Shapes.No)
  val prefix            = "Generic"
  def humanName: String = prefix
  def tpe: Obj.Type     = ???!  // RRR
  val category          = "None"
  def hasMakeDialog     = false

  type E     [S <: stm.Sys[S]]  = Obj[S]
  type Config[S <: stm.Sys[S]]  = Unit

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: Cursor[S]): Unit = ()

  def makeObj[S <: Sys[S]](config: Unit)(implicit tx: S#Tx): List[Obj[S]] = Nil

  def mkTimelineView[S <: Sys[S]](id: S#ID, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): TimelineObjView[S] = {
    val res = new TimelineImpl(tx.newHandle(obj)).initAttrs(id, span, obj)
    res
  }

  def mkGraphemeView[S <: Sys[S]](entry: Grapheme.Entry[S], mode: Mode)
                                 (implicit tx: S#Tx): GraphemeObjView[S] = {
    val res = new GraphemeImpl(tx.newHandle(entry), tx.newHandle(entry.value)).initAttrs(entry)
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

    def entry(implicit tx: S#Tx): Grapheme.Entry[S] = entryH()

    def insets: Insets = Insets.empty

    var succ = Option.empty[GraphemeObjView[S]]
  }
}