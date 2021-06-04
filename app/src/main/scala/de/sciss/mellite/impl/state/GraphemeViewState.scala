/*
 *  GraphemeViewState.scala
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

import de.sciss.lucre.swing.LucreSwing.requireEDT
import de.sciss.lucre.{DoubleObj, Obj, Txn}
import de.sciss.mellite.{DoubleSpan, GraphemeModel, ViewState}

object GraphemeViewState {
  final val Key_YLo    = "y-lo"
  final val Key_YHi    = "y-hi"
}
/** Tracks view state for a Grapheme based object
  * (apart from the timeline state that must be tracked separately).
  * Tracks y-axis bounds.
  */
class GraphemeViewState[T <: Txn[T]](
                                      keyYLo  : String = GraphemeViewState.Key_YLo,
                                      keyYHi  : String = GraphemeViewState.Key_YHi,
                                    ) {
  @volatile
  private var stateYLo  = 0.0
  private var dirtyYLo  = false

  @volatile
  private var stateYHi  = 1.0
  private var dirtyYHi  = false

  def entries(set0: Set[ViewState] = Set.empty): Set[ViewState] = {
    requireEDT()
    var res = set0
    if (dirtyYLo) res += ViewState(keyYLo, DoubleObj, stateYLo   )
    if (dirtyYHi) res += ViewState(keyYHi, DoubleObj, stateYHi    )
    res
  }

  def init(tAttr: Obj.AttrMap[T])(implicit tx: T): Unit = {
    tAttr.$[DoubleObj](keyYLo).foreach { v =>
      stateYLo = v.value
    }
    tAttr.$[DoubleObj](keyYHi).foreach { v =>
      stateYHi = v.value
    }
  }

  def initGUI(gm: GraphemeModel.Modifiable): Unit = {
    gm.yAxisRange = DoubleSpan(stateYLo, stateYHi)

    gm.addListener {
      case GraphemeModel.YAxisRange(_, ch) =>
        stateYLo   = ch.now.start
        stateYHi   = ch.now.stop
        dirtyYLo   = true
        dirtyYHi   = true
    }
  }
}
