/*
 *  AudioCueObjView.scala
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

package de.sciss.mellite.gui.impl.audiocue

import java.awt.datatransfer.Transferable

import de.sciss.desktop
import de.sciss.desktop.FileDialog
import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.graph.AudioFileIn
import de.sciss.lucre.swing.{Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{GainArg, TimeArg}
import de.sciss.mellite.gui.impl.objview.{ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.{ActionArtifactLocation, AudioFileFrame, DragAndDrop, GUI, ObjListView, MessageException, ObjView}
import de.sciss.mellite.{ObjectActions, WorkspaceCache}
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.io.{AudioFile, AudioFileSpec}
import de.sciss.synth.proc.{AudioCue, TimeRef, Universe}
import javax.swing.Icon

import scala.annotation.tailrec
import scala.swing.{Component, Label}
import scala.util.{Failure, Success, Try}

object AudioCueObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = AudioCue.Obj[~] // Grapheme.Expr.Audio[S]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Music)
  val prefix        : String    = "AudioCue"
  def humanName     : String    = "Audio File"
  def tpe           : Obj.Type  = AudioCue.Obj // ElemImpl.AudioGrapheme.typeId
  def category      : String    = ObjView.categResources
  def canMakeObj    : Boolean   = true

  private lazy val dirCache = WorkspaceCache[File]()

  def mkListView[S <: Sys[S]](obj: AudioCue.Obj[S])
                             (implicit tx: S#Tx): AudioCueObjView[S] with ObjListView[S] = {
    val value = obj.value
    new Impl(tx.newHandle(obj), value).init(obj)
  }

  type LocationConfig[S <: stm.Sys[S]] = ActionArtifactLocation.QueryResult[S]

  final case class Config1[S <: stm.Sys[S]](name: String, file: File, spec: AudioFileSpec,
                                            location: LocationConfig[S], offset: Long = 0L, gain: Double = 1.0,
                                            const: Boolean = false)
  type Config[S <: stm.Sys[S]] = List[Config1[S]]

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    import universe.{cursor, workspace}
    val dirIn = cursor.step { implicit tx =>
      dirCache.get()
    }
    val dlg = FileDialog.open(init = dirIn, title = "Add Audio Files")
    dlg.setFilter(f => Try(AudioFile.identify(f).isDefined).getOrElse(false))
    dlg.multiple = true
    val dlgOk = GUI.optionToAborted(dlg.show(window))

    val res = dlgOk.flatMap { f0 =>
      val dirOutOpt = f0.parentOption
      dirOutOpt.foreach { dirOut =>
        cursor.step { implicit tx =>
          dirCache.set(dirOut)
        }
      }
      val fs = dlg.files
      if (fs.isEmpty) Failure(Aborted())
      else {
        @tailrec
        def loop(rem: List[File], locSeq: List[LocationConfig[S]], res: Config[S]): MakeResult[S] =
          rem match {
            case head :: tail =>
              Try(AudioFile.readSpec(head)) match {
                case Failure(ex) => Failure(ex)
                case Success(spec) =>
                  val locExist: Option[LocationConfig[S]] = locSeq.find { case (_, dir) =>
                    val locOk = Try(Artifact.relativize(parent = dir, sub = head)).isSuccess
                    locOk
                  }

                  val locOpt = locExist.orElse(
                    ActionArtifactLocation.query[S](file = head, window = window)(implicit tx => universe.workspace.root)
                  )

                  locOpt match {
                    case Some(loc) =>
                      loop(rem = tail, locSeq = loc :: locSeq, res =
                        Config1(name = head.base, file = head, spec = spec, location = loc) :: res)

                    case None =>
                      Failure(Aborted())
                  }
              }

            case Nil => Success(res.reverse)
        }

        loop(fs, Nil, Nil)
      }
    }
    done(res)
  }

  private final case class Config2(name: Option[String] = None, file: File = null,
                                   location: Option[(String, File)] = None, offset: TimeArg = TimeArg.Frames(0L),
                                   gain: Double = 1.0,
                                   const: Boolean = false)

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    // we do not support any number of files except one
    val default = Config2()
    val p = new ObjViewCmdLineParser[Config2](this)
    import p._
    name((v, c) => c.copy(name = Some(v)))

    opt[File]('l', "location")
      .text("Artifact's base location (directory). If absent, artifact's direct parent is used.")
      .validate(dir => if (dir.isDirectory) success else failure(s"Not a directory: $dir"))
      .action((v, c) => c.copy(location = Some((v.name, v))))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    opt[GainArg]('g', "gain")
      .text(s"Gain (linear, -3dB, ...; default ${default.gain})")
      .action((v, c) => c.copy(gain = v.linear))

    opt[TimeArg]('o', "offset")
      .text(s"Offset into the file (default ${default.offset})")
      .action((v, c) => c.copy(offset = v))

    arg[File]("file")
      .text("File")
      .required()
      .validate { f =>
        val tr = Try(AudioFile.identify(f))
        tr match {
          case Success(Some(_)) => success
          case Success(None)    => failure(s"Cannot identify audio file: $f")
          case Failure(ex)      => failure(s"Cannot identify audio file: ${ex.getMessage}")
        }
      }
      .action { (v, c) => c.copy(file = v) }

    def resolveLoc(f: File, opt: Option[(String, File)]): Try[LocationConfig[S]] = opt match {
      case Some((name, base)) => Success((Right(name), base))
      case None =>
        f.absolute.parentOption match {
          case Some(parent) =>
            val vec = ActionArtifactLocation.find[S](file = f)(implicit tx => universe.workspace.root)
            val exist = vec.collectFirst {
              case l if l.value._2 === parent => (Left(l.value._1), l.value._2): LocationConfig[S]
            }
            val res = exist match {
              case Some(res0) => res0
              case None       => (Right(parent.name), parent)
            }
            Success(res)

          case None => Failure(MessageException(s"No parent directory to '$f'"))
        }
    }

    for {
      c     <- parseConfig(args, default)
      loc   <- resolveLoc(c.file, c.location)
      spec  <- Try(AudioFile.readSpec(c.file))
    } yield {
      val c1 = Config1(name = c.name.getOrElse(c.file.base), file = c.file, spec = spec, location = loc,
        offset = c.offset match {
          case TimeArg.Frames(n) => (n * TimeRef.SampleRate / spec.sampleRate + 0.5).toLong
          case other => other.frames()
        }, gain = c.gain, const = c.const)
      c1 :: Nil
    }
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    var locMade = Map.empty[(String, File), ArtifactLocation[S]]
    var res     = List.empty[Obj[S]]

    config.foreach { c =>
      val loc = c.location match {
        case (Left(source), _) => source()
        case (Right(name), directory) =>
          locMade.get((name, directory)) match {
            case Some(loc0) => loc0
            case None =>
              val loc0 = ActionArtifactLocation.create(name = name, directory = directory)
              locMade += (name, directory) -> loc0
              res ::= loc0
              loc0
          }
      }
      val audioObj = ObjectActions.mkAudioFile(loc, c.file, c.spec, offset = c.offset, gain = c.gain, const = c.const,
        name = Some(c.name))
      res ::= audioObj
    }
    res.reverse
  }

  trait Basic[S <: Sys[S]] extends ObjViewImpl.Impl[S] with AudioCueObjView[S] {
    final override def obj(implicit tx: S#Tx): AudioCue.Obj[S] = objH()

    final def factory: ObjView.Factory = AudioCueObjView

    def isViewable = true

    override def createTransferable(): Option[Transferable] = {
      val t = DragAndDrop.Transferable.files(value.artifact)
      Some(t)
    }

    def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = AudioFileFrame(obj)
      Some(frame)
    }
  }

  private final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, AudioCue.Obj[S]],
                                var value: AudioCue)
    extends ObjListViewImpl.NonEditable[S]
    with Basic[S] {

    type E[~ <: stm.Sys[~]] = AudioCue.Obj[~]

    def init(obj: AudioCue.Obj[S])(implicit tx: S#Tx): this.type = {
      initAttrs(obj)
      disposables ::= obj.changed.react { implicit tx => upd =>
        deferTx {
          value = upd.now
        }
        fire(ObjView.Repaint(this))
      }
      this
    }

    def configureListCellRenderer(label: Label): Component = {
      val txt     = AudioFileIn.specToString(value.spec)
      // XXX TODO: add offset and gain information if they are non-default
      label.text  = txt
      label
    }
  }
}
trait AudioCueObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = AudioCue.Obj[S]

  def value: AudioCue
}