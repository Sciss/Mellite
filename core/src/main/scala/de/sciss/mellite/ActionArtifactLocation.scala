/*
 *  ActionArtifactLocation.scala
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

import java.net.URI

import de.sciss.desktop.{FileDialog, OptionPane, Window}
import de.sciss.asyncfile.Ops._
import de.sciss.file.File
import de.sciss.lucre.{Artifact, ArtifactLocation, Folder, Obj, Source, Txn}
import de.sciss.swingplus.Labeled
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe
import de.sciss.{desktop, equal, swingplus}

import scala.annotation.tailrec
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Dialog
import scala.util.Try

object ActionArtifactLocation {

  type LocationSource [T <: Txn[T]] = Source[T, ArtifactLocation[T]]
  type LocationSourceT[T <: Txn[T]] = (Source[T, ArtifactLocation[T]], URI)
  type QueryResult    [T <: Txn[T]] = (Either[LocationSource[T], String], URI)

  def merge[T <: Txn[T]](result: QueryResult[T])
                        (implicit tx: T): Option[(Option[Obj[T]], ArtifactLocation[T])] = {
    val (list0, loc) = result match {
      case (Left(source), _) => (None, source())
      case (Right(name), directory) =>
        val locM = create(name, directory)
        (Some(locM), locM)
    }
    Some(list0 -> loc) // loc.modifiableOption.map(list0 -> _)
  }

  def query[T <: Txn[T]](file: URI, window: Option[desktop.Window] = None, askName: Boolean = false)
                        (root: T => Folder[T])
                        (implicit universe: Universe[T]): Option[QueryResult[T]] = {

    def createNew(): Option[(String, URI)] = queryNew(child = Some(file), window = window, askName = askName)

    def createNewRes(): Option[QueryResult[T]] =
      createNew().map { case (name, base) => (Right(name), base) }

    val options = find(file = file)(root)

    options match {
      case Vec() => createNewRes()
      case Vec(Labeled((source, base))) => Some((Left(source), base))

      case _ =>
        val ggList = new swingplus.ListView(options)
        ggList.selection.intervalMode = swingplus.ListView.IntervalMode.Single
        ggList.selection.indices += 0
        val opt = OptionPane.apply(message = ggList, messageType = OptionPane.Message.Question,
          optionType = OptionPane.Options.OkCancel, entries = Seq("Ok", "New Location", "Cancel"))
        opt.title = s"Choose Location for ${file.name}"
        val optRes = opt.show(window).id
        // println(s"res = $optRes, ok = ${OptionPane.Result.Ok.id}, cancel = ${OptionPane.Result.Cancel.id}")
        import equal.Implicits._
        if (optRes === 0) {
          ggList.selection.items.headOption.map(v => (Left(v.value._1), v.value._2))
        } else if (optRes === 1) {
          createNewRes()
        } else {
          None
        }
    }
  }

  def find[T <: Txn[T]](file: URI)(root: T => Folder[T])
                       (implicit universe: Universe[T]): Vec[Labeled[LocationSourceT[T]]] = {
    import universe.cursor
    val options: Vec[Labeled[LocationSourceT[T]]] = cursor.step { implicit tx =>
      /* @tailrec */ def loop(xs: List[Obj[T]], res: Vec[Labeled[LocationSourceT[T]]]): Vec[Labeled[LocationSourceT[T]]] =
        xs match {
          case head :: tail =>
            val res1 = head match {
              case objT: ArtifactLocation[T] =>
                val parent = objT.directory
                if (Try(Artifact.Value.relativize(parent, file)).isSuccess) {
                  res :+ Labeled(tx.newHandle(objT) -> parent)(objT.name)
                } else res

              case objT: Folder[T] =>
                loop(objT.iterator.toList, res)

              case _ => res
            }
            loop(tail, res1)

          case Nil => res
      }

      val xs0 = root(tx).iterator.toList
      val _options = loop(xs0, Vector.empty)
      _options
    }

    options
  }

  @tailrec
  def queryNew(child: Option[URI] = None, window: Option[Window] = None, askName: Boolean = false): Option[(String, URI)] = {
    val parentOpt   = child.flatMap(_.parentOption)
    val parentOptF  = parentOpt.flatMap(uri => Try(new File(uri)).toOption)
    val dlg = FileDialog.folder(init = parentOptF, title = "Choose Artifact Base Location")
    dlg.show(None) match {
      case Some(dir) =>
        val dirU = dir.toURI
        child match {
          case Some(file) if Try(Artifact.Value.relativize(dirU, file)).isFailure =>
            queryNew(child, window, askName = askName) // try again
          case _ =>
            val defaultName = dirU.name
            if (askName) {
              val res = Dialog.showInput[String](null,
                "Enter initial store name:", "New Artifact Location",
                Dialog.Message.Question, initial = dirU.name)
              res.map(_ -> dirU)
            } else {
              Some((defaultName, dirU))
            }
        }
      case _=> None
    }
  }

  def create[T <: Txn[T]](name: String, directory: URI)(implicit tx: T): ArtifactLocation[T] = {
    val peer  = ArtifactLocation.newVar[T](directory)
    peer.name = name
    peer
  }
}