/*
 *  TimelineObjViewImpl.scala
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

package de.sciss.mellite.impl

import de.sciss.lucre.expr.CellView
import de.sciss.lucre.{BooleanObj, DoubleObj, Ident, Obj, SpanLikeObj, Txn => LTxn}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.ObjTimelineView.{Context, Factory}
import de.sciss.mellite.impl.objview.GenericObjView
import de.sciss.mellite.impl.timeline.ObjTimelineViewBasicImpl
import de.sciss.mellite.{ObjTimelineView, ObjView, TimelineTool}
import de.sciss.proc.{FadeSpec, ObjKeys, Timeline}

object ObjTimelineViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeId
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[T <: Txn[T]](timed: Timeline.Timed[T], context: Context[T])
                        (implicit tx: T): ObjTimelineView[T] = {
    val span  = timed.span
    val obj   = timed.value
    val tid   = obj.tpe.typeId
    // getOrElse(sys.error(s"No view for type $tid"))
    map.get(tid).fold(GenericObjView.mkTimelineView(timed.id, span, obj)) { f =>
      f.mkTimelineView(timed.id, span, obj.asInstanceOf[f.E[T]], context)
    }
  }

  private var map = Map.empty[Int, Factory]

  // -------- Generic --------

  trait HasGainImpl[T <: LTxn[T]] extends ObjTimelineViewBasicImpl[T] with ObjTimelineView.HasGain {
    var gain: Double = _

    override def initAttrs(id: Ident[T], span: SpanLikeObj[T], obj: Obj[T])(implicit tx: T): this.type = {
      super.initAttrs(id, span, obj)

      val gainView = CellView.attr[T, Double, DoubleObj](obj.attr, ObjKeys.attrGain)
      addDisposable(gainView.react { implicit tx =>opt =>
        deferTx {
          gain = opt.getOrElse(1.0)
        }
        fire(ObjView.Repaint(this))
      })
      gain = gainView().getOrElse(1.0)
      this
    }
  }

  trait HasMuteImpl[T <: LTxn[T]] extends ObjTimelineViewBasicImpl[T] with ObjTimelineView.HasMute {
    var muted: Boolean = _

    override def initAttrs(id: Ident[T], span: SpanLikeObj[T], obj: Obj[T])(implicit tx: T): this.type = {
      super.initAttrs(id, span, obj)

      val muteView = CellView.attr[T, Boolean, BooleanObj](obj.attr, ObjKeys.attrMute)
      addDisposable(muteView.react { implicit tx =>opt =>
        deferTx {
          muted = opt.getOrElse(false)
        }
        fire(ObjView.Repaint(this))
      })
      muted = muteView().getOrElse(false)
      this
    }
  }

  trait HasFadeImpl[T <: LTxn[T]] extends ObjTimelineViewBasicImpl[T] with ObjTimelineView.HasFade {
    var fadeIn : FadeSpec = _
    var fadeOut: FadeSpec = _

    override def initAttrs(id: Ident[T], span: SpanLikeObj[T], obj: Obj[T])(implicit tx: T): this.type = {
      super.initAttrs(id, span, obj)

      val fadeInView = CellView.attr[T, FadeSpec, FadeSpec.Obj](obj.attr, ObjKeys.attrFadeIn)
      addDisposable(fadeInView.react { implicit tx =>opt =>
        deferTx {
          fadeIn = opt.getOrElse(TimelineTool.EmptyFade)
        }
        fire(ObjView.Repaint(this))
      })
      val fadeOutView = CellView.attr[T, FadeSpec, FadeSpec.Obj](obj.attr, ObjKeys.attrFadeOut)
      addDisposable(fadeOutView.react { implicit tx =>opt =>
        deferTx {
          fadeOut = opt.getOrElse(TimelineTool.EmptyFade)
        }
        fire(ObjView.Repaint(this))
      })
      fadeIn  = fadeInView ().getOrElse(TimelineTool.EmptyFade)
      fadeOut = fadeOutView().getOrElse(TimelineTool.EmptyFade)
      this
    }
  }
}