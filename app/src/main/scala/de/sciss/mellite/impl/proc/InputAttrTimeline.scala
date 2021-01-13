/*
 *  InputAttrTimeline.scala
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

package de.sciss.mellite.impl.proc

import de.sciss.fingertree.RangedSeq
import de.sciss.lucre.{Disposable, IdentMap}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import de.sciss.proc

final class InputAttrTimeline[T <: Txn[T]](val parent: ProcObjView.Timeline[T], val key: String,
                                           tl: proc.Timeline[T], tx0: T)
  extends InputAttrImpl[T] {

  override def toString: String = s"InputAttrTimeline(parent = $parent, key = $key)"

  type Entry = proc.Timeline.Timed[T]

  protected def mkTarget(entry: Entry)(implicit tx: T): LinkTarget[T] =
    new LinkTargetTimeline[T](this, tx.newHandle(entry.span), tx.newHandle(entry.value))

  private[this] val tlH = tx0.newHandle(tl)

  def timeline(implicit tx: T): proc.Timeline[T] = tlH()

  protected val viewMap: IdentMap[T, Elem] = tx0.newIdentMap

  // EDT
  private[this] var edtRange = RangedSeq.empty[Elem, Long](_.point, Ordering.Long)

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] =
    edtRange.filterOverlaps((start, stop))

  protected def elemAddedEDT  (elem: Elem): Unit = edtRange += elem
  protected def elemRemovedEDT(elem: Elem): Unit = edtRange -= elem

  private[this] val observer: Disposable[T] =
    tl.changed.react { implicit tx => upd => upd.changes.foreach {
      case proc.Timeline.Added  (span  , entry) =>
        addAttrIn(span, entry = entry, value = entry.value, fire = true)
      case proc.Timeline.Removed(_ /* span */, entry) => removeAttrIn(/* span, */ entryId = entry.id)
      case proc.Timeline.Moved  (spanCh, entry) =>
        removeAttrIn(/* spanCh.before, */ entryId = entry.id)
        addAttrIn   (spanCh.now, entry = entry, value = entry.value, fire = true)
    }} (tx0)

  override def dispose()(implicit tx: T): Unit = {
    super.dispose()
    observer.dispose()
  }

  // ---- init ----
  tl.iterator(tx0).foreach { case (span, xs) =>
    xs.foreach(entry => addAttrIn(span, entry = entry, value = entry.value, fire = false)(tx0))
  }
}

