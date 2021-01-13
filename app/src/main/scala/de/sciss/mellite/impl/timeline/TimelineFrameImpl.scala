/*
 *  TimelineFrameImpl.scala
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

package de.sciss.mellite.impl.timeline

import de.sciss.desktop.{Menu, OptionPane, UndoManager, Window}
import de.sciss.lucre.Source
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.impl.BiGroupImpl
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.impl.proc.ProcObjView
import de.sciss.mellite.{Application, TimelineFrame, TimelineView}
import de.sciss.proc.{Timeline, Universe}

import scala.swing.Action

object TimelineFrameImpl {
  def apply[T <: Txn[T]](group: Timeline[T])
                        (implicit tx: T, universe: Universe[T]): TimelineFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val tlv     = TimelineView[T](group)
    val name    = CellView.name(group)
    import Timeline.format
    val groupH  = tx.newHandle(group)
    val res     = new Impl(tlv, name, groupH)
    res.init()
    res
  }

  private final class Impl[T <: Txn[T]](val view: TimelineView[T], name: CellView[T, String],
                                        groupH: Source[T, Timeline[T]])
    extends WindowImpl[T](name.map(n => s"$n : Timeline"))
    with TimelineFrame[T] {

    import view.{cursor => _cursor}

    override protected def initGUI(): Unit = {
      val mf = Application.windowHandler.menuFactory
      val me = Some(window)

      bindMenus(
        "edit.select-all"         -> view.actionSelectAll,
        "edit.delete"             -> view.actionDelete,
        "actions.stop-all-sound"  -> view.actionStopAllSound,
        // "timeline.splitObjects" -> view.splitObjectsAction,

        "actions.debug-print"     -> Action(null) {
          val it = view.selectionModel.iterator
          if (it.hasNext)
            it.foreach {
              case pv: ProcObjView.Timeline[T] =>
                println(pv.debugString)
                println(_cursor.step { implicit tx => pv.obj.toString() })
                println("--- targets ---")
                println(_cursor.step { implicit tx => pv.targets.mkString("\n") })
              case _ =>
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
              val sel = pane.show(Some(window))
              import de.sciss.equal.Implicits._
              if (sel === OptionPane.Result.Yes) _cursor.step { implicit tx =>
                BiGroupImpl.verifyConsistency(groupH(), reportOnly = false)
              }
            }
          }
        }
      )

      // --- timeline menu ---
      import Menu.{Group, Item}
      val mTimeline = Group("timeline", "Timeline")
        // .add(Item("trimToSelection",    proxy("Trim to Selection",        (menu1 + Key.F5))))
//        .add(Item("insert-span"           , proxy(("Insert Span...",          menu1 + shift + Key.E))))
        .add(Item("clear-span"            , view.actionClearSpan ))
        .add(Item("remove-span"           , view.actionRemoveSpan))
//        .add(Item("dup-span-to-pos"       , "Duplicate Span to Cursor"))
        .addLine()
//        .add(Item("nudge-amount"          , "Nudge Amount..."))
//        .add(Item("nudge-left"            , proxy(("Nudge Objects Backward",  plain + Key.Minus))))
//        .add(Item("nudge-right"           , proxy(("Nudge Objects Forward",   plain + Key.Plus))))
//        .addLine()
        .add(Item("select-following"      , view.actionSelectFollowing))
        .add(Item("align-obj-start-to-pos", view.actionAlignObjectsToCursor))
        .add(Item("split-objects"         , view.actionSplitObjects))
        .add(Item("clean-up-objects"      , view.actionCleanUpObjects))
        .addLine()
//        .add(Item("sel-stop-to-start"     , "Flip Selection Backward"))
//        .add(Item("sel-start-to-stop"     , "Flip Selection Forward"))
//        .addLine()
        .add(Item("drop-marker"           , view.actionDropMarker))
        .add(Item("drop-named-marker"     , view.actionDropNamedMarker))

      window.reactions += {
        case Window.Activated(_) => view.canvas.canvasComponent.requestFocusInWindow()
      }

      mf.add(me, mTimeline)
    }

    // GUI.placeWindow(this, 0f, 0.25f, 24)
  }
}