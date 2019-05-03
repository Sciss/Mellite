/*
 *  InputAttrTimeline.scala
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

import de.sciss.fingertree.RangedSeq
import de.sciss.lucre.stm.{Disposable, IdentifierMap}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget
import de.sciss.synth.proc

final class InputAttrTimeline[S <: Sys[S]](val parent: ProcObjView.Timeline[S], val key: String,
                                           tl: proc.Timeline[S], tx0: S#Tx)
  extends InputAttrImpl[S] {

  override def toString: String = s"InputAttrTimeline(parent = $parent, key = $key)"

  type Entry = proc.Timeline.Timed[S]

  protected def mkTarget(entry: Entry)(implicit tx: S#Tx): LinkTarget[S] =
    new LinkTargetTimeline[S](this, tx.newHandle(entry.span), tx.newHandle(entry.value))

  private[this] val tlH = tx0.newHandle(tl)

  def timeline(implicit tx: S#Tx): proc.Timeline[S] = tlH()

  protected val viewMap: IdentifierMap[S#Id, S#Tx, Elem] = tx0.newInMemoryIdMap

  // EDT
  private[this] var edtRange = RangedSeq.empty[Elem, Long](_.point, Ordering.Long)

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] =
    edtRange.filterOverlaps((start, stop))

  protected def elemAddedEDT  (elem: Elem): Unit = edtRange += elem
  protected def elemRemovedEDT(elem: Elem): Unit = edtRange -= elem

  private[this] val observer: Disposable[S#Tx] =
    tl.changed.react { implicit tx => upd => upd.changes.foreach {
      case proc.Timeline.Added  (span  , entry) =>
        addAttrIn(span, entry = entry, value = entry.value, fire = true)
      case proc.Timeline.Removed(_ /* span */, entry) => removeAttrIn(/* span, */ entryId = entry.id)
      case proc.Timeline.Moved  (spanCh, entry) =>
        removeAttrIn(/* spanCh.before, */ entryId = entry.id)
        addAttrIn   (spanCh.now, entry = entry, value = entry.value, fire = true)
    }} (tx0)

  override def dispose()(implicit tx: S#Tx): Unit = {
    super.dispose()
    observer.dispose()
  }

  // ---- init ----
  tl.iterator(tx0).foreach { case (span, xs) =>
    xs.foreach(entry => addAttrIn(span, entry = entry, value = entry.value, fire = false)(tx0))
  }
}

