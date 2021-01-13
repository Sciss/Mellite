/*
 *  AttrMapFrame.scala
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

import de.sciss.lucre.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.document.{AttrMapFrameImpl => Impl}
import de.sciss.proc.Universe

object AttrMapFrame {
  def apply[T <: Txn[T]](obj: Obj[T])(implicit tx: T, universe: Universe[T]): AttrMapFrame[T] =
    Impl(obj)
}
trait AttrMapFrame[T <: Txn[T]] extends Window[T] {
  def contents: AttrMapView[T]  // XXX TODO - should really be `view`
}