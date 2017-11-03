/*
 *  InputAttrFolder.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui.impl.proc

import de.sciss.lucre.stm.{Disposable, IdentifierMap, Obj}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget
import de.sciss.span.Span
import de.sciss.synth.proc

final class InputAttrFolder[S <: Sys[S]](val parent: ProcObjView.Timeline[S], val key: String,
                                                 f: proc.Folder[S], tx0: S#Tx)
  extends InputAttrImpl[S] {

  override def toString: String = s"InputAttrFolder(parent = $parent, key = $key)"

  type Entry = Obj[S]

  protected def mkTarget(entry: Obj[S])(implicit tx: S#Tx): LinkTarget[S] =
    new LinkTargetFolder[S](this, tx.newHandle(entry))

  private[this] val fH = tx0.newHandle(f)

  def folder(implicit tx: S#Tx): proc.Folder[S] = fH()

  protected val viewMap: IdentifierMap[S#ID, S#Tx, Elem] = tx0.newInMemoryIDMap

  // EDT
  private[this] var edtSet = Set.empty[Elem]  // XXX TODO --- do we need a multi-set in theory?

  protected def elemOverlappingEDT(start: Long, stop: Long): Iterator[Elem] = edtSet.iterator

  protected def elemAddedEDT  (elem: Elem): Unit = edtSet += elem
  protected def elemRemovedEDT(elem: Elem): Unit = edtSet -= elem

  private[this] val observer: Disposable[S#Tx] =
    f.changed.react { implicit tx => upd => upd.changes.foreach {
      case proc.Folder.Added  (_ /* index */, child) =>
        addAttrIn(Span.From(0L), entry = child, value = child, fire = true)
      case proc.Folder.Removed(_ /* index */, child) => removeAttrIn(entryID = child.id)
    }} (tx0)

  override def dispose()(implicit tx: S#Tx): Unit = {
    super.dispose()
    observer.dispose()
  }

  // ---- init ----
  f.iterator(tx0).foreach { child =>
    addAttrIn(Span.From(0L), entry = child, value = child, fire = false)(tx0)
  }
}