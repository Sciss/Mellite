/*
 *  CollectionViewImpl.scala
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
package gui
package impl
package component

import javax.swing.undo.UndoableEdit

import de.sciss.desktop.Window
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.swingplus.PopupMenu

import scala.swing.{Action, BorderPanel, Button, Component, FlowPanel, Swing}

trait CollectionViewImpl[S <: Sys[S]]
  extends ViewHasWorkspace[S]
  with View.Editable[S]
  with ComponentHolder[Component] {

  impl =>

  // ---- abstract ----

  protected def peer: View.Editable[S]

  protected def actionDelete: Action

  protected def selectedObjects: List[ObjView[S]]

  /** Called after the main GUI has been initialized. */
  protected def initGUI2(): Unit

  protected type InsertConfig

  protected def prepareInsert(f: ObjView.Factory): Option[InsertConfig]

  protected def editInsert(f: ObjView.Factory, xs: List[Obj[S]], config: InsertConfig)(implicit tx: S#Tx): Option[UndoableEdit]

  // ---- implemented ----

  lazy final protected val actionAttr: Action = Action(null) {
    val sel = selectedObjects
    val sz  = sel.size
    if (sz > 0) GUI.atomic[S, Unit](nameAttr, s"Opening ${if (sz == 1) "window" else "windows"}") { implicit tx =>
      sel.foreach(n => AttrMapFrame(n.obj))
    }
  }

  lazy final protected val actionView: Action = Action(null) {
    val sel = selectedObjects.filter(_.isViewable)
    val sz  = sel.size
    if (sz > 0) GUI.atomic[S, Unit](nameView, s"Opening ${if (sz == 1) "window" else "windows"}")  { implicit tx =>
      sel.foreach(_.openView(None)) /// XXX TODO - find window
    }
  }

  protected def selectionChanged(sel: List[ObjView[S]]): Unit = {
    val nonEmpty  = sel.nonEmpty
    actionAdd   .enabled = sel.size < 2
    actionDelete.enabled = nonEmpty
    actionView  .enabled = nonEmpty && sel.exists(_.isViewable)
    actionAttr  .enabled = nonEmpty
  }

  final protected var ggAdd   : Button = _
  final protected var ggDelete: Button = _
  final protected var ggView  : Button = _
  final protected var ggAttr  : Button = _

  final def init()(implicit tx: S#Tx): this.type = {
    deferTx(guiInit())
    this
  }

  private final class AddAction(f: ObjView.Factory) extends Action(f.humanName) {
    icon = f.icon

    def apply(): Unit = {
      val winOpt    = Window.find(component)
      f.initMakeDialog[S](workspace, /* parentH, */ winOpt) { conf =>
        val confOpt2  = prepareInsert(f)
        confOpt2.foreach { insConf =>
          val editOpt = cursor.step { implicit tx =>
            val xs = f.makeObj(conf)
            editInsert(f, xs, insConf)
          }
          editOpt.foreach(undoManager.add)
        }
      }
    }
  }

  private lazy val addPopup: PopupMenu = {
    import de.sciss.desktop.Menu._
    val pop     = Popup()
    val tlP     = Application.topLevelObjects
    val flt     = Application.objectFilter
    val f0      = ListObjView.factories.filter(f => f.hasMakeDialog && flt(f.prefix)).toSeq.sortBy(_.humanName)
    val (top0, sub) = f0.partition(f => tlP.contains(f.prefix))
    val top     = tlP.flatMap(prefix => top0.find(_.prefix == prefix))
    top.foreach { f =>
      pop.add(Item(f.prefix, new AddAction(f)))
    }
    val subMap = sub.groupBy(_.category)
    subMap.keys.toSeq.sorted.foreach { categ =>
      val group = Group(categ.toLowerCase, categ)
      subMap.getOrElse(categ, Nil).foreach { f =>
        group.add(Item(f.prefix, new AddAction(f)))
      }
      pop.add(group)
    }

    val window = Window.find(component).getOrElse(sys.error(s"No window for $impl"))
    val res = pop.create(window)
    res.peer.pack() // so we can read `size` correctly
    res
  }

  final protected lazy val actionAdd: Action = Action(null) {
    val bp = ggAdd
    addPopup.show(bp, (bp.size.width - addPopup.size.width) >> 1, bp.size.height - 4)
  }

  private def nameAttr = "Attributes Editor"
  private def nameView = "View Selected Element"

  private def guiInit(): Unit = {
    ggAdd    = GUI.addButton   (actionAdd   , "Add Element")
    ggDelete = GUI.removeButton(actionDelete, "Remove Selected Element")
    ggAttr   = GUI.attrButton  (actionAttr  , nameAttr)
    ggView   = GUI.viewButton  (actionView  , nameView)

    val buttonPanel = new FlowPanel(ggAdd, ggDelete, ggAttr, Swing.HStrut(32), ggView)

    component = new BorderPanel {
      add(impl.peer.component, BorderPanel.Position.Center)
      add(buttonPanel, BorderPanel.Position.South)
    }

    initGUI2()
    selectionChanged(selectedObjects)
  }
}