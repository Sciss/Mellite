/*
 *  InputAttrOutput.scala
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

package de.sciss.mellite.gui.impl.proc

import de.sciss.lucre.stm.IdentifierMap
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget
import de.sciss.span.Span
import de.sciss.synth.proc

final class InputAttrOutput[S <: Sys[S]](val parent: ProcObjView.Timeline[S], val key: String,
                                         out: proc.Output[S], tx0: S#Tx)
  extends InputAttrImpl[S] {

  override def toString: String = s"InputAttrOutput(parent = $parent, key = $key)"

  type Entry = proc.Output[S]

  protected def mkTarget(entry: proc.Output[S])(implicit tx: S#Tx): LinkTarget[S] =
    new LinkTargetOutput[S](this)

  private[this] val outH = tx0.newHandle(out)

  def output(implicit tx: S#Tx): proc.Output[S] = outH()

  protected val viewMap: IdentifierMap[S#Id, S#Tx, Elem] = tx0.newInMemoryIdMap

  // EDT
  private[this] var edtElem: Elem = _

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] = Iterator.single(edtElem)

  protected def elemAddedEDT  (elem: Elem): Unit = edtElem = elem
  protected def elemRemovedEDT(elem: Elem): Unit = ()

  // ---- init ----
  addAttrIn(span = Span.From(0L), entry = out, value = out, fire = false)(tx0)
}
