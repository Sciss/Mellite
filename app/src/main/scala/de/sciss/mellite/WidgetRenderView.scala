/*
 *  WidgetRenderView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.lucre.edit.UndoManager
import de.sciss.lucre.swing.View
import de.sciss.lucre.{synth, Txn => LTxn}
import de.sciss.mellite.impl.widget.WidgetRenderViewImpl
import de.sciss.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}

object WidgetRenderView {
  def apply[T <: synth.Txn[T]](init: Widget[T], bottom: ISeq[View[T]] = Nil, embedded: Boolean = false)
                         (implicit tx: T, universe: Universe[T],
                          undoManager: UndoManager[T]): WidgetRenderView[T] =
    WidgetRenderViewImpl[T](init, bottom, embedded = embedded)

//  sealed trait Update[T <: Txn[T]] { def view: WidgetRenderView[T] }
//  final case class FollowedLink[T <: Txn[T]](view: WidgetRenderView[T], now: Widget[T]) extends Update[T]
}
trait WidgetRenderView[T <: LTxn[T]]
  extends UniverseObjView[T] {

  def widget(implicit tx: T): Widget[T]

  def widget_=(md: Widget[T])(implicit tx: T): Unit

  override def obj(implicit tx: T): Widget[T]

  // def setInProgress(md: Widget[T], value: String)(implicit tx: T): Unit

  // XXX TODO --- we should update View.Editor to use stm.UndoManager
  def undoManager: UndoManager[T]

  def setGraph(g: Widget.Graph)(implicit tx: T): Unit
}
