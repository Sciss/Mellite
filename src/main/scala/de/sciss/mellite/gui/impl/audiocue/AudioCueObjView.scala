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

import de.sciss.audiowidgets.AxisFormat
import de.sciss.desktop
import de.sciss.desktop.FileDialog
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.{Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.{ActionArtifactLocation, AudioFileFrame, GUI, ListObjView, ObjView}
import de.sciss.mellite.{ObjectActions, WorkspaceCache}
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.io.{AudioFile, AudioFileSpec, SampleFormat}
import de.sciss.synth.proc.{AudioCue, Universe}
import javax.swing.Icon

import scala.annotation.tailrec
import scala.swing.{Component, Label}
import scala.util.{Failure, Success, Try}

object AudioCueObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = AudioCue.Obj[~] // Grapheme.Expr.Audio[S]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Music)
  val prefix        : String    = "AudioCue"
  def humanName     : String    = "Audio File"
  def tpe           : Obj.Type  = AudioCue.Obj // ElemImpl.AudioGrapheme.typeId
  def category      : String    = ObjView.categResources
  def canMakeObj    : Boolean   = true

  private lazy val dirCache = WorkspaceCache[File]()

  def mkListView[S <: Sys[S]](obj: AudioCue.Obj[S])
                             (implicit tx: S#Tx): AudioCueObjView[S] with ListObjView[S] = {
    val value = obj.value
    new Impl(tx.newHandle(obj), value).init(obj)
  }

  type LocationConfig[S <: stm.Sys[S]] = ActionArtifactLocation.QueryResult[S]

  final case class Config1[S <: stm.Sys[S]](file: File, spec: AudioFileSpec, location: LocationConfig[S])
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
                      loop(rem = tail, locSeq = loc :: locSeq, res = Config1(file = head, spec = spec, location = loc) :: res)

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

  override def initMakeCmdLine[S <: Sys[S]](args: List[String]): MakeResult[S] = ???

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    var locMade = Map.empty[(String, File), ArtifactLocation[S]]
    var res     = List.empty[Obj[S]]

    config.foreach { cfg =>
      val loc = cfg.location match {
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
      val audioObj = ObjectActions.mkAudioFile(loc, cfg.file, cfg.spec)
      res ::= audioObj
    }
    res.reverse
  }

  private val timeFmt = AxisFormat.Time(hours = false, millis = true)

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, AudioCue.Obj[S]],
                                var value: AudioCue)
    extends AudioCueObjView[S]
    with ListObjView /* .AudioGrapheme */[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.NonEditable[S] {

    override def obj(implicit tx: S#Tx): AudioCue.Obj[S] = objH()

    type E[~ <: stm.Sys[~]] = AudioCue.Obj[~]

    def factory: ObjView.Factory = AudioCueObjView

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

    def isViewable = true

    def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = AudioFileFrame(obj)
      Some(frame)
    }

    def configureRenderer(label: Label): Component = {
      // ex. AIFF, stereo 16-bit int 44.1 kHz, 0:49.492
      val spec    = value.spec
      val smp     = spec.sampleFormat
      val isFloat = smp match {
        case SampleFormat.Float | SampleFormat.Double => "float"
        case _ => "int"
      }
      val chans   = spec.numChannels match {
        case 1 => "mono"
        case 2 => "stereo"
        case n => s"$n-chan."
      }
      val sr  = f"${spec.sampleRate/1000}%1.1f"
      val dur = timeFmt.format(spec.numFrames.toDouble / spec.sampleRate)

      // XXX TODO: add offset and gain information if they are non-default
      val txt    = s"${spec.fileType.name}, $chans ${smp.bitsPerSample}-$isFloat $sr kHz, $dur"
      label.text = txt
      label
    }
  }
}
trait AudioCueObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): AudioCue.Obj[S]

  override def objH: stm.Source[S#Tx, AudioCue.Obj[S]]

  def value: AudioCue
}