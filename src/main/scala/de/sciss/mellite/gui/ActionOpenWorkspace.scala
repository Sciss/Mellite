/*
 *  ActionOpenWorkspace.scala
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

import java.util.concurrent.TimeUnit

import de.sciss.desktop
import de.sciss.desktop.{Desktop, FileDialog, KeyStrokes, Menu, OptionPane, RecentFiles, Util}
import de.sciss.file._
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.lucre.swing.LucreSwing.defer
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{Application, GUI, Mellite, Prefs}
import de.sciss.synth.proc
import de.sciss.synth.proc.{Confluent, Durable, SoundProcesses, Universe, Workspace}
import javax.swing.SwingUtilities

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, blocking}
import scala.language.existentials
import scala.swing.event.Key
import scala.swing.{Action, Dialog}
import scala.util.{Failure, Success}

object ActionOpenWorkspace extends Action("Open...") {
  import KeyStrokes._

  private val _recent = RecentFiles(Application.userPrefs("recent-docs")) { folder =>
    perform(folder)
  }

  accelerator = Some(menu1 + Key.O)

  private def dh = Application.documentHandler
  private def fullTitle = "Open Workspace"

  private[this] lazy val _init: Unit =
    Desktop.addListener {
      case Desktop.OpenFiles(_, files) =>
        // println(s"TODO: $open; EDT? ${java.awt.EventQueue.isDispatchThread}")
        files.foreach { f =>
          ActionOpenWorkspace.perform(f)
        }
    }

  def install(): Unit = _init

  // XXX TODO: should be in another place
  def openGUI[S <: Sys[S]](universe: Universe[S]): Unit = {
    universe.workspace.folder.foreach(recentFiles.add)
    dh.addDocument(universe)
    universe.workspace match {
      case cf: Workspace.Confluent =>
        implicit val workspace: Workspace.Confluent  = cf
        implicit val cursor: stm.Cursor[Durable] = workspace.system.durable
        GUI.atomic[proc.Durable, Unit](fullTitle, s"Opening cursor window for '${cf.name}'") { implicit tx =>
          implicit val u: Universe[Confluent] = universe.asInstanceOf[Universe[Confluent]]
          DocumentCursorsFrame(cf)
        }
      case eph =>
        implicit val cursor: stm.Cursor[S] = eph.cursor
        val nameView = CellView.const[S, String](eph.name)
        GUI.atomic[S, Unit](fullTitle, s"Opening root elements window for '${eph.name}'") { implicit tx =>
//          implicit val universe: Universe[S] = Universe(GenContext[S](), Scheduler[S](), Mellite.auralSystem)
          implicit val u: Universe[S] = universe
          FolderFrame[S](name = nameView, isWorkspaceRoot = true)
        }
    }
  }

  def recentFiles: RecentFiles  = _recent
  def recentMenu : Menu.Group   = _recent.menu

  def apply(): Unit = {
    val dlg = FileDialog.folder(title = fullTitle)
    // import TypeCheckedTripleEquals._
    // dlg.setFilter { f => f.isDirectory && f.ext.toLowerCase === Workspace.ext}
    dlg.show(None).foreach(perform)
  }

//  private def openView[S <: Sys[S]](universe: Universe[S]): Unit = ()
//// MMM
////    DocumentViewHandler.instance(doc).collectFirst {
////      case dcv: DocumentCursorsView => dcv.window
////    } .foreach(_.front())

  def perform(folder: File): Future[Universe[_]] = {
//    val fOpt = Some(folder)
    dh.documents.find(_.workspace.folder.contains(folder)).fold(doOpen(folder)) { u =>
//      val u1 = u.asInstanceOf[Universe[S] forSome { type S <: Sys[S] }]
      // openView(u1)
      Mellite.withUniverse(u)(Future.successful)
    }
  }

  private def doOpen(folder: File): Future[Universe[_]] = {
    import SoundProcesses.executionContext
    val config          = BerkeleyDB.Config()
    config.allowCreate  = false
//    config.readOnly     = true
    config.lockTimeout  = Duration(Prefs.dbLockTimeout.getOrElse(Prefs.defaultDbLockTimeout), TimeUnit.MILLISECONDS)
    val ds              = BerkeleyDB.factory(folder, config)
    val fut: Future[Universe[~] forSome { type ~ <: Sys[~] }] = Future {  // IntelliJ highlight bug
      val w = blocking(Workspace.read(folder, ds))
      Mellite.mkUniverse(w)
    }

    var opt: OptionPane[Unit] = null
    desktop.Util.delay(1000) {
      if (!fut.isCompleted) {
        opt = OptionPane.message(message = s"Reading '$folder'…")
        opt.show(None, "Open Workspace")
      }
    }
    fut.onComplete { tr =>
      defer {
        if (opt != null) {
          val w = SwingUtilities.getWindowAncestor(opt.peer)
          if (w != null) w.dispose()
        }
        tr match {
          case Success(ws) => openGUI(ws)
//          case Success(cf : Workspace.Confluent) => openGUI(cf )
//          case Success(eph: Workspace.Durable)   => openGUI(eph)
//          case Success(eph: Workspace.InMemory)  => openGUI(eph)
          case Failure(e) =>
            Dialog.showMessage(
              message     = s"Unable to create new workspace ${folder.path}\n\n${Util.formatException(e)}",
              title       = fullTitle,
              messageType = Dialog.Message.Error
            )
          // case _ =>
        }
      }
    }
    fut
  }
}