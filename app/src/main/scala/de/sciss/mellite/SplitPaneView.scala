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

import de.sciss.lucre.LTxn
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder

import scala.swing.{Orientation, SplitPane}

object SplitPaneView {
  def apply[T <: Txn[T]](left: View[T], right: View[T], orientation: Orientation.Value)
                        (implicit tx: T): SplitPaneView[T] = {
    new Impl(left, right, orientation).init()
  }

  private final class Impl[T <: Txn[T]](val left: View[T], val right: View[T], orientation: Orientation.Value)
    extends SplitPaneView[T] with ComponentHolder[SplitPane] {

    def init()(implicit tx: T): this.type = {
      deferTx(guiInit())
      this
    }

    private def guiInit(): Unit = {
      component = new SplitPane(orientation, left.component, right.component)
    }

    def dispose()(implicit tx: T): Unit = {
      left  .dispose()
      right .dispose()
    }
  }
}
trait SplitPaneView[T <: Txn[T]] extends View[T] {
  type C = SplitPane

  def left  : View[T]
  def right : View[T]
}