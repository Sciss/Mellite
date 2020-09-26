/*
 *  ActionNewWorkspace.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import java.util.concurrent.TimeUnit

import de.sciss.desktop.{FileDialog, KeyStrokes, OptionPane, Util}
import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.lucre.store.BerkeleyDB
import de.sciss.lucre.synth.{InMemory, Txn}
import de.sciss.lucre.{DataStore, Workspace}
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe
import javax.swing.JDialog

import scala.concurrent.duration.Duration
import scala.swing.event.Key
import scala.swing.{Action, Button, Label}
import scala.util.control.NonFatal

object ActionNewWorkspace extends Action("Workspace...") {
  import KeyStrokes._

  accelerator = Some(menu1 + shift + Key.N)

  private def deleteRecursive(f: File): Boolean = {
    if (f.isDirectory) {
      val arr = f.listFiles()
      var i = 0
      while (i < arr.length) {
        val f1 = arr(i)
        if (!deleteRecursive(f1)) return false
        i += 1
      }
    }
    f.delete()
  }

  private def fullTitle = "New Workspace"

  def apply(): Unit = {
    val tpeMessage = new Label("""<HTML><BODY><B>Workspaces can be confluent or ephemeral.</B><P><br>
        |A <I>confluent</I> workspace keeps a trace of its history.<P><br>
        |An <I>ephemeral</I> workspace does not remember its history.<br>
        |An ephemeral workspace can either <I>durable</I> (stored on disk) or purely <I>in-memory</I>.
        |""".stripMargin
    )

    var tpeRes = -1

    // XXX TODO --- we need a way to do this elegantly in Desktop
    lazy val tpeEntries: Seq[Button] = Seq(("Confluent", Key.C), ("Durable", Key.D), ("In-Memory", Key.I)).zipWithIndex
      .map { case ((txt, key), ti) =>
        val b = Button(txt) {
          tpeRes = ti
          dlg.dispose()
        }
        b.mnemonic = key
        b
      }

    lazy val tpeInitial = tpeEntries(1)
    lazy val tpeDlg     = OptionPane(message = tpeMessage, entries = tpeEntries, initial = Some(tpeInitial))
    tpeDlg.title        = fullTitle
    lazy val dlg: JDialog = tpeDlg.peer.createDialog(null, tpeDlg.title)
    dlg.setVisible(true)
    if (tpeRes < 0) return
    val confluent   = tpeRes == 0
    val inMemory    = tpeRes == 2

    if      (inMemory)  performInMemory()
    else if (confluent) performConfluent()
    else                performDurable()
  }

  def performInMemory(): (proc.Workspace.InMemory, Universe[InMemory.Txn]) = {
    val w   = proc.Workspace.InMemory()
    val u   = Mellite.mkUniverse(w)
    OpenWorkspace.openGUI(u)
    (w, u)
  }

  def performDurable(): Option[(proc.Workspace.Durable, Universe[proc.Durable.Txn])] =
    create[proc.Durable.Txn, proc.Workspace.Durable] { (folder, config) =>
      proc.Workspace.Durable.empty(folder, config)
    }

  def performConfluent(): Option[(proc.Workspace.Confluent, Universe[proc.Confluent.Txn])] =
    create[proc.Confluent.Txn, proc.Workspace.Confluent] { (folder, config) =>
      proc.Workspace.Confluent.empty(folder, config)
    }

  private def selectFile(): Option[File] = {
    val fileDlg = FileDialog.save(title = "Location for New Workspace")
    fileDlg.show(None).flatMap { folder0 =>
      val name    = folder0.name
      val folder  = if (folder0.ext.toLowerCase == proc.Workspace.ext)
        folder0
      else
        folder0.parent / s"$name.${proc.Workspace.ext}"

      val folderOpt = Some(folder)
      val isOpen = Application.documentHandler.documents.exists(_.workspace.folder === folderOpt)

      if (isOpen) {
        val optOvr = OptionPane.message(
          message     = s"Workspace ${folder.path} already exists and is currently open.",
          messageType = OptionPane.Message.Error
        )
        optOvr.title = fullTitle
        optOvr.show()
        None

      } else if (!folder.exists()) Some(folder) else {
        val optOvr = OptionPane.confirmation(
          message     = s"Workspace ${folder.path} already exists.\nAre you sure you want to overwrite it?",
          optionType  = OptionPane.Options.OkCancel,
          messageType = OptionPane.Message.Warning
        )
        optOvr.title = fullTitle
        val resOvr = optOvr.show()
        val isOk = resOvr === OptionPane.Result.Ok

        if (!isOk) None else if (deleteRecursive(folder)) Some(folder) else {
          val optUnable = OptionPane.message(
            message     = s"Unable to delete existing workspace ${folder.path}",
            messageType = OptionPane.Message.Error
          )
          optUnable.title = fullTitle
          optUnable.show()
          None
        }
      }
    }
  }

  private def create[T <: Txn[T], A <: Workspace[T]](fun: (File, DataStore.Factory) => A): Option[(A, Universe[T])] =
    selectFile().flatMap { folder =>
      try {
        val config          = BerkeleyDB.Config()
        config.allowCreate  = true
        val ds              = BerkeleyDB.factory(folder, config)
        config.lockTimeout  = Duration(Prefs.dbLockTimeout.getOrElse(Prefs.defaultDbLockTimeout), TimeUnit.MILLISECONDS)
        val w               = fun(folder, ds)
        val u               = Mellite.mkUniverse(w)
        OpenWorkspace.openGUI(u)
        Some((w, u))

      } catch {
        case NonFatal(e) =>
          val optUnable = OptionPane.message(
            message     = s"Unable to create new workspace ${folder.path} \n\n${Util.formatException(e)}",
            messageType = OptionPane.Message.Error
          )
          optUnable.title = fullTitle
          optUnable.show()
          None
      }
    }
}
