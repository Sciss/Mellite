/*
 *  InputElem.scala
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

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Disposable
import de.sciss.mellite.ObjTimelineView
import de.sciss.mellite.impl.proc.ProcObjView.LinkTarget
import de.sciss.span.SpanLike

/* Reference to an element referred to by an input-attr.
 * Source views are updated by calling `copy` as they appear and disappear
 */
final class InputElem[S <: stm.Sys[T]](val span: SpanLike, val source: Option[ProcObjView.Timeline[T]],
                                       val target: LinkTarget[T], obs: Disposable[T], tx0: T)
  extends Disposable[T] {

  source.foreach(_.addTarget(target)(tx0))

  def point: (Long, Long) = ObjTimelineView.spanToPoint(span)

  def dispose()(implicit tx: T): Unit = {
    obs.dispose()
    source.foreach(_.removeTarget(target))
  }

  def copy(newSource: Option[ProcObjView.Timeline[T]])(implicit tx: T): InputElem[T] = {
    source.foreach(_.removeTarget(target))
    new InputElem(span = span, source = newSource, target = target, obs = obs, tx0 = tx)
  }
}

