/*
 *  SplitPaneView.scala
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

package de.sciss.mellite

import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder

import scala.swing.{Orientation, SplitPane}

object SplitPaneView {
  def apply[S <: Sys[S]](left: View[S], right: View[S], orientation: Orientation.Value)
                        (implicit tx: S#Tx): SplitPaneView[S] = {
    new Impl(left, right, orientation).init()
  }

  private final class Impl[S <: Sys[S]](val left: View[S], val right: View[S], orientation: Orientation.Value)
    extends SplitPaneView[S] with ComponentHolder[SplitPane] {

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      this
    }

    private def guiInit(): Unit = {
      component = new SplitPane(orientation, left.component, right.component)
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      left  .dispose()
      right .dispose()
    }
  }
}
trait SplitPaneView[S <: Sys[S]] extends View[S] {
  type C = SplitPane

  def left  : View[S]
  def right : View[S]
}