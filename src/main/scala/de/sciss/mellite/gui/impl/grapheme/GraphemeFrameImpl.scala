/*
 *  GraphemeFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */


package de.sciss.mellite
package gui
package impl
package grapheme

import de.sciss.desktop.{KeyStrokes, Menu, UndoManager, Window}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Grapheme, Universe}

import scala.swing.event.Key

object GraphemeFrameImpl {
  def apply[S <: Sys[S]](group: Grapheme[S])
                        (implicit tx: S#Tx, universe: Universe[S]): GraphemeFrame[S] = {
    implicit val undoMgr: UndoManager = UndoManager()
    val tlv     = GraphemeView[S](group)
    val name    = CellView.name(group)
    import Grapheme.serializer
    val groupH  = tx.newHandle(group)
    val res     = new Impl(tlv, name, groupH)
    res.init()
    res
  }

  private final class Impl[S <: Sys[S]](val view: GraphemeView[S], name: CellView[S#Tx, String],
                                        groupH: stm.Source[S#Tx, Grapheme[S]])
    extends WindowImpl[S](name.map(n => s"$n : Grapheme"))
      with GraphemeFrame[S] {

    override protected def initGUI(): Unit = {
      val mf = Application.windowHandler.menuFactory
      val me = Some(window)

//      bindMenus(
//        "edit.delete" -> view.actionDelete
//      )

      // --- grapheme menu ---
      import KeyStrokes._
      import Menu.{Group, Item, proxy}
      val mGrapheme = Group("grapheme", "Grapheme")
        .add(Item("insert-span"           , proxy(("Insert Span...",          menu1 + shift + Key.E))))
//        .add(Item("clear-span"            , view.actionClearSpan ))
//        .add(Item("remove-span"           , view.actionRemoveSpan))
        .add(Item("dup-span-to-pos"       , "Duplicate Span to Cursor"))
        .addLine()
        .add(Item("nudge-amount"          , "Nudge Amount..."))
        .add(Item("nudge-left"            , proxy(("Nudge Objects Backward",  plain + Key.Minus))))
        .add(Item("nudge-right"           , proxy(("Nudge Objects Forward",   plain + Key.Plus))))
        .addLine()
        .add(Item("select-following"      , proxy(("Select Following Objects", menu2 + Key.F))))
//        .add(Item("move-obj-start-to-pos" , view.actionMoveObjectToCursor))
        .addLine()
        .add(Item("sel-stop-to-start",     "Flip Selection Backward"))
        .add(Item("sel-start-to-stop",     "Flip Selection Forward"))

      window.reactions += {
        case Window.Activated(_) => view.canvas.canvasComponent.requestFocusInWindow()
      }

      mf.add(me, mGrapheme)
    }
  }
}