/*
 *  TimelineViewBaseImpl.scala
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

package de.sciss.mellite.impl

import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{GUI, ObjView, TimelineViewBase}
import de.sciss.mellite.AttrMapFrame

import scala.swing.{Action, Component}

trait TimelineViewBaseImpl[S <: Sys[S], Y, Child <: ObjView[S]]
  extends TimelineViewBase[S, Y,Child] {

  private[this] var _ggChildAttr: Component = _
  private[this] var _ggChildView: Component = _

  protected def guiInit(): Unit = {
    val actionChildAttr = Action(null) {
      withSelection { implicit tx =>
        seq => {
          seq.foreach { view =>
            AttrMapFrame(view.obj)
          }
          None
        }
      }
    }

    val actionChildView = Action(null) {
      withFilteredSelection(_.isViewable) { implicit tx =>
        seq => {
          val windowOption = Window.find(this)
          seq.foreach(_.openView(windowOption))
          None
        }
      }
    }

    actionChildAttr.enabled = false
    actionChildView.enabled = false
    
    val _ggAttr = GUI.attrButton(actionChildAttr, "Attributes Editor")
    _ggAttr.focusable = false
    _ggChildAttr = _ggAttr

    val _ggView = GUI.viewButton(actionChildView, "View Selected Element")
    _ggView.focusable = false
    _ggChildView = _ggView

    selectionModel.addListener {
      case _ =>
        val selMod  = selectionModel
        val hasSome = selMod.nonEmpty
        if (actionChildAttr.enabled != hasSome) {
          actionChildAttr.enabled = hasSome
        }
        if (actionChildView.enabled != hasSome) {
          val hasSome1 = hasSome && selMod.iterator.exists(_.isViewable)
          actionChildView.enabled = hasSome1
        }
    }
  }

  /** Component for viewing the attributes of the currently selected children. */
  protected final def ggChildAttr: Component = _ggChildAttr
  /** Component for viewing the editors of the currently selected children. */
  protected final def ggChildView: Component = _ggChildView

  protected final def withSelection[A](fun: S#Tx => TraversableOnce[Child] => Option[A]): Option[A] =
    if (selectionModel.isEmpty) None else {
      val sel = selectionModel.iterator
      cursor.step { implicit tx => fun(tx)(sel) }
    }

  protected final def withFilteredSelection[A](p: Child => Boolean)
                                        (fun: S#Tx => TraversableOnce[Child] => Option[A]): Option[A] = {
    val sel = selectionModel.iterator
    val flt = sel.filter(p)
    if (flt.hasNext) cursor.step { implicit tx => fun(tx)(flt) } else None
  }
}
