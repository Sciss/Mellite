/*
 *  InputAttrOutput.scala
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

package de.sciss.mellite.impl.proc

import de.sciss.lucre.IdentMap
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import de.sciss.span.Span
import de.sciss.proc.Proc

final class InputAttrOutput[T <: Txn[T]](val parent: ProcObjView.Timeline[T], val key: String,
                                         out: Proc.Output[T], tx0: T)
  extends InputAttrImpl[T] {

  override def toString: String = s"InputAttrOutput(parent = $parent, key = $key)"

  type Entry = Proc.Output[T]

  protected def mkTarget(entry: Proc.Output[T])(implicit tx: T): LinkTarget[T] =
    new LinkTargetOutput[T](this)

  private[this] val outH = tx0.newHandle(out)

  def output(implicit tx: T): Proc.Output[T] = outH()

  protected val viewMap: IdentMap[T, Elem] = tx0.newIdentMap

  // EDT
  private[this] var edtElem: Elem = _

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] = Iterator.single(edtElem)

  protected def elemAddedEDT  (elem: Elem): Unit = edtElem = elem
  protected def elemRemovedEDT(elem: Elem): Unit = ()

  // ---- init ----
  addAttrIn(span = Span.From(0L), entry = out, value = out, fire = false)(tx0)
}
