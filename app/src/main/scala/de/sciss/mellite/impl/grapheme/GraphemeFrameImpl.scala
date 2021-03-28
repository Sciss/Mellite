/*
 *  GraphemeFrameImpl.scala
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

package de.sciss.mellite.impl.grapheme

import de.sciss.desktop.{KeyStrokes, Menu, UndoManager, Window}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.WorkspaceWindow
import de.sciss.mellite.{Application, GraphemeFrame, GraphemeView}
import de.sciss.proc.{Grapheme, Universe}

import scala.swing.event.Key

object GraphemeFrameImpl {
  def apply[T <: Txn[T]](group: Grapheme[T])
                        (implicit tx: T, universe: Universe[T]): GraphemeFrame[T] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val tlv     = GraphemeView[T](group)
    val name    = CellView.name(group)
    val res     = new Impl(tlv, name)
    res.init()
    res
  }

  private final class Impl[T <: Txn[T]](val view: GraphemeView[T], name: CellView[T, String])
    extends WorkspaceWindow[T](name.map(n => s"$n : Grapheme"))
      with GraphemeFrame[T] {

    override protected def initGUI(): Unit = {
      super.initGUI()

      val mf = Application.windowHandler.menuFactory
      val me = Some(window)

      bindMenus(
        "edit.select-all"         -> view.actionSelectAll,
        "edit.delete"             -> view.actionDelete
      )

      // --- grapheme menu ---
      import KeyStrokes._
      import Menu.{Group, Item, proxy}
      val mGrapheme = Group("grapheme", "Grapheme")
        .add(Item("insert-span"           , proxy(("Insert Span...",          menu1 + shift + Key.E))))
//        .add(Item("clear-span"            , view.actionClearSpan ))
//        .add(Item("remove-span"           , view.actionRemoveSpan))
//        .add(Item("dup-span-to-pos"       , "Duplicate Span to Cursor"))
        .addLine()
//        .add(Item("nudge-amount"          , "Nudge Amount..."))
//        .add(Item("nudge-left"            , proxy(("Nudge Objects Backward",  plain + Key.Minus))))
//        .add(Item("nudge-right"           , proxy(("Nudge Objects Forward",   plain + Key.Plus))))
//        .addLine()
        .add(Item("select-following"      , view.actionSelectFollowing))
//        .add(Item("align-obj-start-to-pos", view.actionAlignObjectsToCursor))
//        .add(Item("split-objects"         , view.actionSplitObjects))
//        .add(Item("clean-up-objects"      , view.actionCleanUpObjects))
//        .addLine()
//        .add(Item("drop-marker"           , view.actionDropMarker))
//        .add(Item("drop-named-marker"     , view.actionDropNamedMarker))

      window.reactions += {
        case Window.Activated(_) => view.canvas.canvasComponent.requestFocusInWindow()
      }

      mf.add(me, mGrapheme)
    }
  }
}