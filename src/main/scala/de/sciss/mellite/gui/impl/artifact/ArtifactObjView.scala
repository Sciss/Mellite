/*
 *  ArtifactObjView.scala
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

package de.sciss.mellite.gui.impl.artifact

import de.sciss.desktop
import de.sciss.desktop.{FileDialog, PathField}
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.{ActionArtifactLocation, ListObjView, MessageException, ObjView}
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.FlowPanel
import scala.swing.event.SelectionChanged
import scala.util.{Failure, Success}

object ArtifactObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Artifact[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.PagePortrait)
  val prefix        : String    = "Artifact"
  def humanName     : String    = "File"
  def tpe           : Obj.Type  = Artifact
  def category      : String    = ObjView.categResources
  def canMakeObj : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Artifact[S])(implicit tx: S#Tx): ArtifactObjView[S] with ListObjView[S] = {
    val peer      = obj
    val value     = peer.value  // peer.child.path
    val editable  = false // XXX TODO -- peer.modifiableOption.isDefined
    new Impl[S](tx.newHandle(obj), value, isEditable = editable).init(obj)
  }

  type LocationConfig[S <: stm.Sys[S]] = ActionArtifactLocation.QueryResult[S]
  final case class Config[S <: stm.Sys[S]](name: String, file: File, location: LocationConfig[S])

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggFile  = new PathField
    ggFile.mode = FileDialog.Save
    val ggMode  = new ComboBox(Seq("New File", "Existing File", "Existing Folder")) {
      listenTo(selection)
      reactions += {
        case SelectionChanged(_) =>
          ggFile.mode = selection.index match {
            case 1 => FileDialog.Open
            case 2 => FileDialog.Folder
            case _ => FileDialog.Save
          }
      }
    }
    val ggValue = new FlowPanel(ggFile, ggMode)
    val res = ObjViewImpl.primitiveConfig[S, File](window, tpe = prefix, ggValue = ggValue,
      prepare = ggFile.valueOption match {
        case Some(value) => Success(value)
        case None => Failure(MessageException("No file was specified"))
      })
    val res1 = res.flatMap { case (name, f) =>
      val locOpt = ActionArtifactLocation.query[S](file = f, window = window, askName = true)(implicit tx => universe.workspace.root)
      locOpt.fold[MakeResult[S]](Failure(Aborted())) { location =>
        val cfg = Config(name = name, file = f, location = location)
        Success(cfg)
      }
    }
    done(res1)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
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

  private final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Artifact[S]],
                                        var file: File, val isEditable: Boolean)
    extends ArtifactObjView[S]
      with ListObjView[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.StringRenderer
      with ObjViewImpl.NonViewable[S] {

    type E[~ <: stm.Sys[~]] = Artifact[~]

    def factory: ObjView.Factory = ArtifactObjView

    def value: File = file

    override def obj(implicit tx: S#Tx): Artifact[S] = objH()

    def init(obj: Artifact[S])(implicit tx: S#Tx): this.type = {
      initAttrs(obj)
      disposables ::= obj.changed.react { implicit tx => upd =>
        deferAndRepaint {
          file = upd.now
        }
      }
      this
    }

    def tryEdit(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = None // XXX TODO
  }
}
trait ArtifactObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Artifact[S]
}