/*
 *  TimelineObjViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl.timeline

import de.sciss.lucre.expr.{BooleanObj, DoubleObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.TimelineObjView.{Context, Factory}
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.mellite.gui.impl.{ActionView, GenericObjView}
import de.sciss.synth.proc.{FadeSpec, ObjKeys, Timeline}

object TimelineObjViewImpl {
  private val sync = new AnyRef

  def addFactory(f: Factory): Unit = sync.synchronized {
    val tid = f.tpe.typeId
    if (map.contains(tid)) throw new IllegalArgumentException(s"View factory for type $tid already installed")
    map += tid -> f
  }

  def factories: Iterable[Factory] = map.values

  def apply[S <: Sys[S]](timed: Timeline.Timed[S], context: Context[S])
                        (implicit tx: S#Tx): TimelineObjView[S] = {
    val span  = timed.span
    val obj   = timed.value
    val tid   = obj.tpe.typeId
    // getOrElse(sys.error(s"No view for type $tid"))
    map.get(tid).fold(GenericObjView.mkTimelineView(timed.id, span, obj)) { f =>
      f.mkTimelineView(timed.id, span, obj.asInstanceOf[f.E[S]], context)
    }
  }

  private var map = Map[Int, Factory](
    ProcObjView .tpe.typeId -> ProcObjView,
    ActionView  .tpe.typeId -> ActionView
  )

  // -------- Generic --------

  trait HasGainImpl[S <: stm.Sys[S]] extends TimelineObjViewBasicImpl[S] with TimelineObjView.HasGain {
    var gain: Double = _

    override def initAttrs(id: S#Id, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): this.type = {
      super.initAttrs(id, span, obj)

      val gainView = AttrCellView[S, Double, DoubleObj](obj.attr, ObjKeys.attrGain)
      disposables ::= gainView.react { implicit tx => opt =>
        deferTx {
          gain = opt.getOrElse(1.0)
        }
        fire(ObjView.Repaint(this))
      }
      gain = gainView().getOrElse(1.0)
      this
    }
  }

  trait HasMuteImpl[S <: stm.Sys[S]] extends TimelineObjViewBasicImpl[S] with TimelineObjView.HasMute {
    var muted: Boolean = _

    override def initAttrs(id: S#Id, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): this.type = {
      super.initAttrs(id, span, obj)

      val muteView = AttrCellView[S, Boolean, BooleanObj](obj.attr, ObjKeys.attrMute)
      disposables ::= muteView.react { implicit tx => opt =>
        deferTx {
          muted = opt.getOrElse(false)
        }
        fire(ObjView.Repaint(this))
      }
      muted = muteView().getOrElse(false)
      this
    }
  }

  trait HasFadeImpl[S <: stm.Sys[S]] extends TimelineObjViewBasicImpl[S] with TimelineObjView.HasFade {
    var fadeIn : FadeSpec = _
    var fadeOut: FadeSpec = _

    override def initAttrs(id: S#Id, span: SpanLikeObj[S], obj: Obj[S])(implicit tx: S#Tx): this.type = {
      super.initAttrs(id, span, obj)

      val fadeInView = AttrCellView[S, FadeSpec, FadeSpec.Obj](obj.attr, ObjKeys.attrFadeIn)
      disposables ::= fadeInView.react { implicit tx => opt =>
        deferTx {
          fadeIn = opt.getOrElse(TrackTool.EmptyFade)
        }
        fire(ObjView.Repaint(this))
      }
      val fadeOutView = AttrCellView[S, FadeSpec, FadeSpec.Obj](obj.attr, ObjKeys.attrFadeOut)
      disposables ::= fadeOutView.react { implicit tx => opt =>
        deferTx {
          fadeOut = opt.getOrElse(TrackTool.EmptyFade)
        }
        fire(ObjView.Repaint(this))
      }
      fadeIn  = fadeInView ().getOrElse(TrackTool.EmptyFade)
      fadeOut = fadeOutView().getOrElse(TrackTool.EmptyFade)
      this
    }
  }
}