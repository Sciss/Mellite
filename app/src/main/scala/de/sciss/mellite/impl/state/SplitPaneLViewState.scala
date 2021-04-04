/*
 *  SplitPaneLViewState.scala
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
import de.sciss.lucre.{BooleanObj, IntObj, Obj, Txn}
import de.sciss.mellite.ViewState

import java.beans.PropertyChangeEvent
import javax.swing.JSplitPane
import scala.swing.{Dimension, SplitPane}

/** Tracks view state for a SplitPane object, assuming
  * the auxiliary component is in left or top position.
  * Tracks divider location, and a 'shown' state corresponding
  * with a location of zero (i.e. left or top component hidden)
  */
class SplitPaneLViewState[T <: Txn[T]](
                                       keyShown  : String,
                                       keyWidth  : String,
                                     ) {
  @volatile
  private var stateShown  = true
  private var dirtyShown  = false

  @volatile
  private var stateWidth  = 0
  private var dirtyWidth  = false

  def entries(set0: Set[ViewState] = Set.empty): Set[ViewState] = {
    requireEDT()
    var res = set0
    if (dirtyShown) res += ViewState(keyShown , BooleanObj, stateShown)
    if (dirtyWidth) res += ViewState(keyWidth , IntObj    , stateWidth)
    res
  }

  def init(tAttr: Obj.AttrMap[T])(implicit tx: T): Unit = {
    tAttr.$[BooleanObj](keyShown).foreach { v =>
      stateShown = v.value
    }
    tAttr.$[IntObj](keyWidth).foreach { v =>
      stateWidth = v.value
    }
  }

  def initGUI(pane: SplitPane): Unit = {
    if (stateWidth > 0) {
      pane.dividerLocation = stateWidth
    }
    if (!stateShown && pane.oneTouchExpandable) {
      // cf. https://stackoverflow.com/questions/4934499/how-to-set-jsplitpane-divider-collapse-expand-state
      pane.leftComponent.minimumSize = new Dimension
      pane.dividerLocation = 0
    }

    pane.peer.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, { _: PropertyChangeEvent =>
      val newLoc = pane.dividerLocation
      if (newLoc > 0) {
        if (!stateShown) {
          stateShown  = true
          dirtyShown  = true
        }
        if (stateWidth != newLoc) {
          stateWidth  = newLoc
          dirtyWidth  = true
        }

      } else {
        if (stateShown) {
          stateShown  = false
          dirtyShown  = true
        }
      }
    })
  }
}
