package de.sciss.mellite.gui.impl.proc

import de.sciss.lucre.stm
import de.sciss.lucre.stm.Disposable
import de.sciss.mellite.gui.TimelineObjView
import de.sciss.mellite.gui.impl.proc.ProcObjView.LinkTarget
import de.sciss.span.SpanLike

/* Reference to an element referred to by an input-attr.
 * Source views are updated by calling `copy` as they appear and disappear
 */
final class InputElem[S <: stm.Sys[S]](val span: SpanLike, val source: Option[ProcObjView.Timeline[S]],
                                               val target: LinkTarget[S], obs: Disposable[S#Tx], tx0: S#Tx)
  extends Disposable[S#Tx] {

  source.foreach(_.addTarget(target)(tx0))

  def point: (Long, Long) = TimelineObjView.spanToPoint(span)

  def dispose()(implicit tx: S#Tx): Unit = {
    obs.dispose()
    source.foreach(_.removeTarget(target))
  }

  def copy(newSource: Option[ProcObjView.Timeline[S]])(implicit tx: S#Tx): InputElem[S] = {
    source.foreach(_.removeTarget(target))
    new InputElem(span = span, source = newSource, target = target, obs = obs, tx0 = tx)
  }
}

