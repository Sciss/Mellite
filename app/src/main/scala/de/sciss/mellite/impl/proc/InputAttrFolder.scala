/*
 *  InputAttrFolder.scala
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

import de.sciss.lucre.stm.{Disposable, Folder, IdentifierMap, Obj}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import de.sciss.span.Span

final class InputAttrFolder[T <: Txn[T]](val parent: ProcObjView.Timeline[T], val key: String,
                                         f: Folder[T], tx0: T)
  extends InputAttrImpl[T] {

  override def toString: String = s"InputAttrFolder(parent = $parent, key = $key)"

  type Entry = Obj[T]

  protected def mkTarget(entry: Obj[T])(implicit tx: T): LinkTarget[T] =
    new LinkTargetFolder[T](this, tx.newHandle(entry))

  private[this] val fH = tx0.newHandle(f)

  def folder(implicit tx: T): Folder[T] = fH()

  protected val viewMap: IdentifierMap[S#Id, T, Elem] = tx0.newInMemoryIdMap

  // EDT
  private[this] var edtSet = Set.empty[Elem]  // XXX TODO --- do we need a multi-set in theory?

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] = edtSet.iterator

  protected def elemAddedEDT  (elem: Elem): Unit = edtSet += elem
  protected def elemRemovedEDT(elem: Elem): Unit = edtSet -= elem

  private[this] val observer: Disposable[T] =
    f.changed.react { implicit tx => upd => upd.changes.foreach {
      case Folder.Added  (_ /* index */, child) =>
        addAttrIn(Span.From(0L), entry = child, value = child, fire = true)
      case Folder.Removed(_ /* index */, child) => removeAttrIn(entryId = child.id)
    }} (tx0)

  override def dispose()(implicit tx: T): Unit = {
    super.dispose()
    observer.dispose()
  }

  // ---- init ----
  f.iterator(tx0).foreach { child =>
    addAttrIn(Span.From(0L), entry = child, value = child, fire = false)(tx0)
  }
}