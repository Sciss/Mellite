/*
 *  MenuBar.scala
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

package de.sciss.mellite.gui

import de.sciss.desktop.KeyStrokes.{menu1, shift}
import de.sciss.desktop.{Desktop, KeyStrokes, Menu}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{Application, Mellite}
import de.sciss.osc
import javax.swing.KeyStroke

import scala.concurrent.stm.TxnExecutor
import scala.swing.event.Key

object MenuBar {
  def keyUndo     : KeyStroke = menu1 + Key.Z
  def keyRedo     : KeyStroke = if (Desktop.isWindows) menu1 + Key.Y else menu1 + shift + Key.Z
  def keyClose    : KeyStroke = menu1 + Key.W
  def keyShowLog  : KeyStroke = menu1 + Key.P
  def keyClearLog : KeyStroke = menu1 + shift + Key.P

  lazy val instance: Menu.Root = {
    import KeyStrokes._
    import Menu._

    val itPrefs = Item.Preferences(Application)(ActionPreferences())
    val itQuit  = Item.Quit(Application)
//    val itAbout = Item.About(Application)(showAbout())

    val mFile = Group("file", "File")
      .add(Group("new", "New")
        .add(Item("new-doc", ActionNewWorkspace))
        .add(Item("repl",    InterpreterFrame.Action))
      )
      .add(Item("open", ActionOpenWorkspace))
      .add(ActionOpenWorkspace.recentMenu)
      .add(Item("close", proxy(("Close", keyClose))))
      .add(Item("close-all", ActionCloseAllWorkspaces))
      .addLine()
      .add(Item("bounce", proxy((s"${ActionBounce.title}...", menu1 + Key.B))))
      // .add(Item("bounce-transform",   proxy("Bounce And Transform...",  (menu1 + shift + Key.B))))
    if (itQuit.visible) mFile.addLine().add(itQuit)

    val mEdit = Group("edit", "Edit")
      .add(Item("undo",               proxy(("Undo",                    keyUndo))))
      .add(Item("redo",               proxy(("Redo",                    keyRedo))))
      .addLine()
      .add(Item("cut",                proxy(("Cut",                     menu1 + Key.X))))
      .add(Item("copy",               proxy(("Copy",                    menu1 + Key.C))))
      .add(Item("paste",              proxy(("Paste",                   menu1 + Key.V))))
      .add(Item("delete",             proxy(("Delete",                  plain + Key.BackSpace))))
      .addLine()
      .add(Item("select-all",         proxy(("Select All",              menu1 + Key.A))))

    if (itPrefs.visible /* && Desktop.isLinux */) mEdit.addLine().add(itPrefs)

    val mActions = Group("actions", "Actions")
      .add(Item("stop-all-sound",     proxy(("Stop All Sound",          menu1 + Key.Period))))
      .add(Item("debug-print",        proxy(("Debug Print",             menu2 + Key.P))))
      .add(Item("debug-threads")("Debug Thread Dump")(debugThreads()))
      .add(Item("dump-osc")("Dump OSC" -> (ctrl + shift + Key.D))(dumpOSC()))
//      .add(Item("window-shot",        proxy("Export Window as PDF...")))

    val mView = Group("view", "View")
      .add(Item("show-log" )("Show Log Window"  -> keyShowLog )(Mellite.logToFront()))
      .add(Item("clear-log")("Clear Log Window" -> keyClearLog)(Mellite.clearLog  ()))

    // if (itPrefs.visible && !Desktop.isLinux) mOperation.addLine().add(itPrefs)

//    val gHelp = Group("help", "Help")
//    if (itAbout.visible) gHelp.add(itAbout)
//    gHelp
//      .add(Item("index")("Online Documentation")(
//        Desktop.browseURI(new URI(Mellite.homepage))))
//      .add(Item("issues")("Report a Bug")(
//        Desktop.browseURI(new URI("https://github.com/Sciss/Mellite/issues"))))

    val res = Root()
      .add(mFile)
      .add(mEdit)
      .add(mActions)
      .add(mView)
//      .add(gHelp)
    // .add(mTimeline)
    // .add(mOperation)

    res
  }

  private def debugThreads(): Unit = {
    import scala.collection.JavaConverters._
    val m = Thread.getAllStackTraces.asScala.toVector.sortBy(_._1.getId)
    println("Id__ State_________ Name___________________ Pri")
    m.foreach { case (t, _) =>
      println(f"${t.getId}%3d  ${t.getState}%-13s  ${t.getName}%-23s  ${t.getPriority}%2d")
    }
    m.foreach { case (t, stack) =>
      println()
      println(f"${t.getId}%3d  ${t.getState}%-13s  ${t.getName}%-23s")
      if (t == Thread.currentThread()) println("    (self)")
      else stack.foreach { elem =>
        println(s"    $elem")
      }
    }
  }

  private var dumpMode: osc.Dump = osc.Dump.Off

  private def dumpOSC(): Unit = {
    val sOpt = TxnExecutor.defaultAtomic { itx =>
      implicit val tx: Txn = Txn.wrap(itx)
      Mellite.auralSystem.serverOption
    }
    sOpt.foreach { s =>
      dumpMode = if (dumpMode == osc.Dump.Off) osc.Dump.Text else osc.Dump.Off
      s.peer.dumpOSC(dumpMode, filter = {
        case m: osc.Message if m.name == "/$meter" => false
        case _ => true
      })
      println(s"DumpOSC is ${if (dumpMode == osc.Dump.Text) "ON" else "OFF"}")
    }
  }
}