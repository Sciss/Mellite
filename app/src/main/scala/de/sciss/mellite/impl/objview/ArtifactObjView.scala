/*
 *  ArtifactObjView.scala
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

package de.sciss.mellite.impl.objview

import java.net.URI

import de.sciss.desktop
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.{Artifact, Cursor, Obj, Source, Txn => LTxn}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.impl.artifact.ArtifactViewImpl
import de.sciss.mellite.{ActionArtifactLocation, ArtifactFrame, MessageException, ObjListView, ObjView}
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.util.{Failure, Success}

object ArtifactObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = Artifact[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.PagePortrait)
  val prefix        : String    = "Artifact"
  def humanName     : String    = "File"
  def tpe           : Obj.Type  = Artifact
  def category      : String    = ObjView.categResources
  def canMakeObj : Boolean   = true

  def mkListView[T <: Txn[T]](obj: Artifact[T])(implicit tx: T): ArtifactObjView[T] with ObjListView[T] = {
    val peer      = obj
    val value     = peer.value  // peer.child.path
    val editable  = false // XXX TODO -- peer.modifiableOption.isDefined
    new Impl[T](tx.newHandle(obj), value, isListCellEditable = editable).init(obj)
  }

  type LocationConfig[T <: LTxn[T]] = ActionArtifactLocation.QueryResult[T]
  final case class Config[T <: LTxn[T]](name: String, file: URI, location: LocationConfig[T])

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val (ggFile, ggValue) = ArtifactViewImpl.mkPathField(reveal = false, mode = true)
    val res = ObjViewImpl.primitiveConfig[T, URI](window, tpe = prefix, ggValue = ggValue,
      prepare = ggFile.valueOption match {
        case Some(value)  => Success(value.toURI)
        case None         => Failure(MessageException("No file was specified"))
      })
    val res1 = res.flatMap { c =>
      import c._
      val locOpt = ActionArtifactLocation.query[T](file = value, window = window, askName = true)(implicit tx => universe.workspace.root)
      locOpt.fold[MakeResult[T]](Failure(Aborted())) { location =>
        val cfg = Config(name = name, file = value, location = location)
        Success(cfg)
      }
    }
    done(res1)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val location: Opt[File] = opt(
        descr = "Artifact's base location (directory). If absent, artifact's direct parent is used."
      )
      validateFileIsDirectory(location)
      val file: Opt[File] = trailArg(descr = "File")

      validateOpt(location, file) {
        case (Some(_), _)     => Right(())
        case (None, Some(f))  =>
          if (f.absolute.parentOption.isDefined) Right(()) else Left(s"File $f does not have parent directory")
        case (None, None)     => Left("File not specified")
       }
    }
    p.parse(Config(name = p.name(), file = p.file().toURI, location = p.location.toOption match {
      case Some(v) => Right(v.name) -> v.toURI
      case None =>
        val parent = p.file().absolute.parent
        Right(parent.name) -> parent.toURI
    }))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val (list0, loc) = location match {
      case (Left(source), _) => (Nil, source())
      case (Right(nameLoc), directory) =>
        val objLoc  = ActionArtifactLocation.create(name = nameLoc, directory = directory)
        (objLoc :: Nil, objLoc)
    }
    val art = Artifact(loc, file)
    if (!name.isEmpty) art.name = name
    art :: list0
  }

  private final class Impl[T <: Txn[T]](val objH: Source[T, Artifact[T]],
                                        var uri: URI, val isListCellEditable: Boolean)
    extends ArtifactObjView[T]
      with ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.StringRenderer {

    type E[~ <: LTxn[~]] = Artifact[~]

    def factory: ObjView.Factory = ArtifactObjView

    def value: URI = uri

    override def obj(implicit tx: T): Artifact[T] = objH()

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = ArtifactFrame[T](objH(), mode = true)
      Some(frame)
    }

    def init(obj: Artifact[T])(implicit tx: T): this.type = {
      initAttrs(obj)
      addDisposable(obj.changed.react { implicit tx =>upd =>
        deferAndRepaint {
          uri = upd.now
        }
      })
      this
    }

    def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = None // XXX TODO
  }
}
trait ArtifactObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Artifact[T]
}