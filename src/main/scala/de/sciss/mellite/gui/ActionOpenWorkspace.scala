/*
 *  ActionOpenWorkspace.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import java.util.concurrent.TimeUnit

import javax.swing.SwingUtilities
import de.sciss.desktop
import de.sciss.desktop.{FileDialog, KeyStrokes, Menu, OptionPane, RecentFiles, Util}
import de.sciss.file._
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.store.BerkeleyDB
import de.sciss.lucre.swing.defer
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc
import de.sciss.synth.proc.{Durable, SoundProcesses, Workspace, WorkspaceLike}

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

  // XXX TODO: should be in another place
  def openGUI[S <: Sys[S]](doc: Workspace[S]): Unit = {
    doc.folder.foreach(recentFiles.add)
    dh.addDocument(doc)
    doc match {
      case cf: Workspace.Confluent =>
        implicit val workspace: Workspace.Confluent  = cf
        implicit val cursor: stm.Cursor[Durable] = workspace.system.durable
        GUI.atomic[proc.Durable, Unit](fullTitle, s"Opening cursor window for '${doc.name}'") {
          implicit tx => DocumentCursorsFrame(cf)
        }
      case eph =>
        implicit val workspace: Workspace[S] = eph
        implicit val cursor: stm.Cursor[S] = eph.cursor
        val nameView = CellView.const[S, String](doc.name)
        GUI.atomic[S, Unit](fullTitle, s"Opening root elements window for '${doc.name}'") {
          implicit tx => FolderFrame[S](name = nameView, isWorkspaceRoot = true)
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

  private def openView[S <: Sys[S]](doc: Workspace[S]): Unit = ()
// MMM
//    DocumentViewHandler.instance(doc).collectFirst {
//      case dcv: DocumentCursorsView => dcv.window
//    } .foreach(_.front())

  def perform(folder: File): Unit = {
    import de.sciss.equal.Implicits._
    val fOpt = Some(folder)
    dh.documents.find(_.folder === fOpt).fold(doOpen(folder)) { doc =>
      val doc1 = doc.asInstanceOf[Workspace[S] forSome { type S <: Sys[S] }]
      openView(doc1)
    }
  }

  private def doOpen(folder: File): Unit = {
    import SoundProcesses.executionContext
    val config          = BerkeleyDB.Config()
    config.allowCreate  = false
    config.lockTimeout  = Duration(Prefs.dbLockTimeout.getOrElse(Prefs.defaultDbLockTimeout), TimeUnit.MILLISECONDS)
    val ds              = BerkeleyDB.factory(folder, config)
    val fut: Future[WorkspaceLike] = Future(blocking(Workspace.read(folder, ds)))

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
          case Success(cf : Workspace.Confluent) => openGUI(cf )
          case Success(eph: Workspace.Durable)   => openGUI(eph)
          case Success(eph: Workspace.InMemory)  => openGUI(eph)
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
  }
}