/*
 *  TimelineViewState.scala
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

package de.sciss.mellite.impl.state

import de.sciss.audiowidgets.{TimelineModel, TransportCatch}
import de.sciss.lucre.swing.LucreSwing.requireEDT
import de.sciss.lucre.{BooleanObj, DoubleObj, LongObj, Obj, SpanLikeObj, SpanObj, Txn}
import de.sciss.mellite.{GUI, ViewState}
import de.sciss.numbers.Implicits._
import de.sciss.span.Span

import scala.math.sqrt
import scala.swing.Slider

object TimelineViewState {
  final val Key_Position    = "tl-pos"
  final val Key_Visible     = "tl-vis"
  final val Key_Selection   = "tl-sel"
  final val Key_Catch       = "catch"
  final val Key_VisualBoost = "vis-boost"

//  final val Default_VisualBoostMin  =   1.0
//  final val Default_VisualBoostMax  = 512.0
}
/** Tracks view state for a Timeline based object with transport.
  * Tracks timeline position, visible and selected span, as well as visual boost and transport catch.
  */
class TimelineViewState[T <: Txn[T]](
                                      keyPosition   : String = TimelineViewState.Key_Position,
                                      keyVisible    : String = TimelineViewState.Key_Visible,
                                      keySelection  : String = TimelineViewState.Key_Selection,
                                      keyCatch      : String = TimelineViewState.Key_Catch,
                                      keyVisualBoost: String = TimelineViewState.Key_VisualBoost,
                                      visBoostMin   : Double = GUI.Default_VisualBoostMin,
                                      visBoostMax   : Double = GUI.Default_VisualBoostMax,
                                    ) {
  @volatile
  private var statePosition     = 0L
  private var dirtyPosition     = false

  @volatile
  private var stateVisible      = Span(0L, 0L)
  private var dirtyVisible      = false

  @volatile
  private var stateSelection    = Span.Void: Span.SpanOrVoid
  private var dirtySelection    = false

  @volatile
  private var stateCatch        = true
  private var dirtyCatch        = false

  @volatile
  private var stateVisualBoost  = sqrt(visBoostMin * visBoostMax)
  private var dirtyVisualBoost  = false

  def entries: Set[ViewState] = {
    requireEDT()
    var res = Set.empty[ViewState]
    if (dirtyPosition   ) res += ViewState(TimelineViewState.Key_Position     , LongObj     , statePosition   )
    if (dirtyVisible    ) res += ViewState(TimelineViewState.Key_Visible      , SpanObj     , stateVisible    )
    if (dirtySelection  ) res += ViewState(TimelineViewState.Key_Selection    , SpanLikeObj , stateSelection  )
    if (dirtyCatch      ) res += ViewState(TimelineViewState.Key_Catch        , BooleanObj  , stateCatch      )
    if (dirtyVisualBoost) res += ViewState(TimelineViewState.Key_VisualBoost  , DoubleObj   , stateVisualBoost)
    res
  }

  def init(tAttr: Obj.AttrMap[T])(implicit tx: T): Unit = {
    tAttr.$[LongObj](keyPosition).foreach { v =>
      statePosition = v.value
    }
    tAttr.$[SpanObj](keyVisible).foreach { v =>
      stateVisible = v.value
    }
    tAttr.$[SpanLikeObj](keySelection).foreach { v =>
      v.value match {
        case sp: Span => stateSelection = sp
        case _ =>
      }
    }
    tAttr.$[BooleanObj](keyCatch).foreach { v =>
      stateCatch = v.value
    }
    tAttr.$[DoubleObj](keyVisualBoost).foreach { v =>
      stateVisualBoost = v.value
    }
  }

  def initGUI(tlm: TimelineModel.Modifiable, cch: TransportCatch, visBoost: Slider): Unit = {
    tlm.position = statePosition
    if (stateVisible.nonEmpty) {
      tlm.visible = stateVisible
    }
    tlm.selection = stateSelection

    tlm.addListener {
      case TimelineModel.Position (_, p) =>
        statePosition   = p.now
        dirtyPosition   = true

      case TimelineModel.Visible(_, sp) =>
        stateVisible    = sp.now
        dirtyVisible    = true

      case TimelineModel.Selection(_, sp) =>
        stateSelection  = sp.now
        dirtySelection  = true
    }

    cch.catchEnabled = stateCatch
    cch.addListener {
      case b =>
        stateCatch  = b
        dirtyCatch  = true
    }

    if (stateVisualBoost >= visBoostMin && stateVisualBoost <= visBoostMax) {
      val visBoostView0 = (stateVisualBoost.expLin(
        visBoostMin, visBoostMax, visBoost.min, visBoost.max) + 0.5).toInt
        .clip(visBoost.min, visBoost.max)
      visBoost.value = visBoostView0
    }
    visBoost.peer.addChangeListener { _ =>
      val visBoostModel = visBoost.value.linExp(visBoost.min, visBoost.max, visBoostMin, visBoostMax)
      if (stateVisualBoost != visBoostModel) {
        stateVisualBoost  = visBoostModel
        dirtyVisualBoost  = true
      }
    }
  }
}
