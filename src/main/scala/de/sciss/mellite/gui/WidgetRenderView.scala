/*
 *  WidgetRenderView.scala
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

import de.sciss.lucre.event.Observable
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.impl.widget.WidgetRenderViewImpl
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}

object WidgetRenderView {
  def apply[S <: SSys[S]](init: Widget[S], bottom: ISeq[View[S]] = Nil, embedded: Boolean = false)
                         (implicit tx: S#Tx, universe: Universe[S]): WidgetRenderView[S] =
    WidgetRenderViewImpl[S](init, bottom, embedded = embedded)

  sealed trait Update[S <: Sys[S]] { def view: WidgetRenderView[S] }
  final case class FollowedLink[S <: Sys[S]](view: WidgetRenderView[S], now: Widget[S]) extends Update[S]
}
trait WidgetRenderView[S <: Sys[S]]
  extends UniverseView[S] with Observable[S#Tx, WidgetRenderView.Update[S]] {

  def widget(implicit tx: S#Tx): Widget[S]

  def widget_=(md: Widget[S])(implicit tx: S#Tx): Unit

  // def setInProgress(md: Widget[S], value: String)(implicit tx: S#Tx): Unit

  def setGraph(g: Widget.Graph)(implicit tx: S#Tx): Unit
}
