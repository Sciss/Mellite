/*
 *  MenuBar.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.desktop.{Desktop, KeyStrokes, Menu}
import scala.swing.event.Key

object MenuBar {
  lazy val instance: Menu.Root = {
    import Menu._
    import KeyStrokes._

    val itPrefs = Item.Preferences(Application)(ActionPreferences())
    val itQuit  = Item.Quit(Application)

    val mFile = Group("file", "File")
      .add(Group("new", "New")
        .add(Item("new-doc", ActionNewWorkspace))
        .add(Item("repl",    InterpreterFrame.Action))
      )
      .add(Item("open", ActionOpenWorkspace))
      .add(ActionOpenWorkspace.recentMenu)
      .add(Item("close",              proxy("Close",                    menu1 + Key.W)))
      .add(Item("close-all",          "Close All"))
      .addLine()
      .add(Item("bounce",             proxy("Bounce...",                menu1 + Key.B)))
      // .add(Item("bounce-transform",   proxy("Bounce And Transform...",  (menu1 + shift + Key.B))))
    if (itQuit.visible) mFile.addLine().add(itQuit)

    val keyRedo = if (Desktop.isWindows) menu1 + Key.Y else menu1 + shift + Key.Z

    val mEdit = Group("edit", "Edit")
      .add(Item("undo",               proxy("Undo",                     menu1 + Key.Z)))
      .add(Item("redo",               proxy("Redo",                     keyRedo)))
      .addLine()
      .add(Item("cut",                proxy("Cut",                      menu1 + Key.X)))
      .add(Item("copy",               proxy("Copy",                     menu1 + Key.C)))
      .add(Item("paste",              proxy("Paste",                    menu1 + Key.V)))
      .add(Item("delete",             proxy("Delete",                   plain + Key.BackSpace)))
      .addLine()
      .add(Item("select-all",         proxy("Select All",               menu1 + Key.A)))

    if (itPrefs.visible /* && Desktop.isLinux */) mEdit.addLine().add(itPrefs)

    val mActions = Group("actions", "Actions")
      .add(Item("stop-all-sound",     proxy("Stop All Sound",           menu1 + Key.Period)))
      .add(Item("debug-print",        proxy("Debug Print",              menu2 + Key.P)))
      .add(Item("window-shot",        proxy("Export Window as PDF...")))

    val mView = Group("view", "View")
      .add(Item("show-log" )("Show Log Window"  -> (menu1         + Key.P))(Mellite.logToFront()))
      .add(Item("clear-log")("Clear Log Window" -> (menu1 + shift + Key.P))(Mellite.clearLog  ()))

    //    // --- timeline menu ---
    //    val mTimeline = Group("timeline", "Timeline")
    //      // .add(Item("trimToSelection",    proxy("Trim to Selection",        (menu1 + Key.F5))))
    //      .add(Item("insertSpan",         proxy("Insert Span...",           menu1 + shift + Key.E)))
    //      .add(Item("clearSpan",          proxy("Clear Selected Span",      menu1 + Key.BackSlash)))
    //      .add(Item("removeSpan",         proxy("Remove Selected Span",     menu1 + shift + Key.BackSlash)))
    //      .add(Item("dupSpanToPos",       "Duplicate Span to Cursor"))
    //      .addLine()
    //      .add(Item("nudgeAmount",        "Nudge Amount..."))
    //      .add(Item("nudgeLeft",          proxy("Nudge Objects Backward",   plain + Key.Minus)))
    //      .add(Item("nudgeRight",         proxy("Nudge Objects Forward",    plain + Key.Plus)))
    //      .addLine()
    //      .add(Item("selectFollowing",    proxy("Select Following Objects", menu2 + Key.F)))
    //      .add(Item("alignObjStartToPos", "Align Objects Start To Cursor"))
    //      .add(Item("splitObjects",       proxy("Split Selected Objects",   menu2 + Key.Y)))
    //      .addLine()
    //      .add(Item("selStopToStart",     "Flip Selection Backward"))
    //      .add(Item("selStartToStop",     "Flip Selection Forward"))

    //    val mOperation = Group("operation", "Operation")
    //      .add(Item("cursorFollows",      "Cursor Follows Playback Head"))

    // if (itPrefs.visible && !Desktop.isLinux) mOperation.addLine().add(itPrefs)

    Root()
      .add(mFile)
      .add(mEdit)
      .add(mActions)
      .add(mView)
      // .add(mTimeline)
      // .add(mOperation)
  }
}
