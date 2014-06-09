/*
 *  FrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
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
package timeline

import de.sciss.synth.proc.{ProcGroupElem, Obj, ProcGroup}
import de.sciss.mellite.Document
import de.sciss.lucre.stm
import de.sciss.desktop.{KeyStrokes, Menu, OptionPane, Window}
import scala.swing.event.Key
import scala.swing.{Component, Action}
import de.sciss.lucre.bitemp.impl.BiGroupImpl
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.swing.deferTx
import de.sciss.synth.proc
import proc.Implicits._

object FrameImpl {
  def apply[S <: Sys[S]](document: Document[S], group: Obj.T[S, ProcGroupElem])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): TimelineFrame[S] = {
    val tlv     = TimelineView[S](document, group)
    val name    = group.attr.name
    import ProcGroup.serializer
    val groupH  = tx.newHandle(group.elem.peer)
    val res     = new Impl(tlv, name, groupH)
    deferTx {
      res.init()
    }
    res
  }

  private final class Impl[S <: Sys[S]](view: TimelineView[S], name: String,
                                        groupH: stm.Source[S#Tx, ProcGroup[S]])
                                       (implicit _cursor: stm.Cursor[S])
    extends TimelineFrame[S] with WindowHolder[Window] {

    def dispose()(implicit tx: S#Tx): Unit = {
      disposeData()
      deferTx(window.dispose())
    }

    private def disposeData()(implicit tx: S#Tx): Unit =
      view.dispose()

    private def frameClosing(): Unit =
      _cursor.step { implicit tx =>
        disposeData()
      }

    def contents: TimelineView[S] = view

    def component: Component = view.component

    def init(): Unit = {
      val frame = new WindowImpl {
        // component.peer.getRootPane.putClientProperty("apple.awt.brushMetalLook", true)
        title       = s"$name : Timeline"
        contents    = view.component

        bindMenus(
          "file.bounce"           -> view.bounceAction,
          "edit.delete"           -> view.deleteAction,
          "actions.stopAllSound"  -> view.stopAllSoundAction,
          // "timeline.splitObjects" -> view.splitObjectsAction,
          "actions.debugPrint"    -> Action(null) {
            val it = view.procSelectionModel.iterator
            if (it.hasNext)
              it.foreach { pv =>
                println(pv.debugString)
              }
            else {
              val (treeS, opt) = _cursor.step { implicit tx =>
                val s1 = groupH().debugPrint
                val s2 = BiGroupImpl.verifyConsistency(groupH(), reportOnly = true)
                (s1, s2)
              }
              if (opt.isEmpty) {
                println("No problems found!")
              } else {
                println(treeS)
                println()
                opt.foreach(println)

                val pane = OptionPane.confirmation(message = "Correct the data structure?",
                  optionType = OptionPane.Options.YesNo, messageType = OptionPane.Message.Warning)
                pane.title = "Sanitize Timeline"
                val sel = pane.show(Some(this))
                if (sel == OptionPane.Result.Yes) _cursor.step { implicit tx =>
                  BiGroupImpl.verifyConsistency(groupH(), reportOnly = false)
                }
              }
            }
          }
        )

        // --- timeline menu ---
        import Menu.{Group, Item, proxy}
        import KeyStrokes._
        private val mTimeline = Group("timeline", "Timeline")
          // .add(Item("trimToSelection",    proxy("Trim to Selection",        (menu1 + Key.F5))))
          .add(Item("insertSpan",         proxy("Insert Span...",           menu1 + shift + Key.E)))
          .add(Item("clearSpan",          proxy("Clear Selected Span",      menu1 + Key.BackSlash)))
          .add(Item("removeSpan",         proxy("Remove Selected Span",     menu1 + shift + Key.BackSlash)))
          .add(Item("dupSpanToPos",       "Duplicate Span to Cursor"))
          .addLine()
          .add(Item("nudgeAmount",        "Nudge Amount..."))
          .add(Item("nudgeLeft",          proxy("Nudge Objects Backward",   plain + Key.Minus)))
          .add(Item("nudgeRight",         proxy("Nudge Objects Forward",    plain + Key.Plus)))
          .addLine()
          .add(Item("selectFollowing",    proxy("Select Following Objects", menu2 + Key.F)))
          .add(Item("alignObjStartToPos", "Align Objects Start To Cursor"))
          // .add(Item("splitObjects",       proxy("Split Selected Objects",   menu2 + Key.Y)))
          .add(Item("splitObjects", view.splitObjectsAction))
          .addLine()
          .add(Item("selStopToStart",     "Flip Selection Backward"))
          .add(Item("selStartToStop",     "Flip Selection Forward"))

        handler.menuFactory.add(Some(this), mTimeline)

        reactions += {
          case Window.Closing(_) => frameClosing()
        }

        pack()
        // centerOnScreen()
        GUI.placeWindow(this, 0f, 0.25f, 24)
        front()
      }

      view.component.peer.putClientProperty("de.sciss.mellite.Window", frame)

      window = frame
    }
  }
}