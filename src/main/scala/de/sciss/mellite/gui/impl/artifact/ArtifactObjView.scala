/*
 *  ArtifactObjView.scala
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

package de.sciss.mellite.gui.impl.artifact

import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.desktop
import de.sciss.desktop.{FileDialog, PathField}
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.{ListObjViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.{ActionArtifactLocation, ListObjView, ObjView}
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Workspace

import scala.swing.FlowPanel
import scala.swing.event.SelectionChanged

object ArtifactObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Artifact[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.PagePortrait)
  val prefix        : String    = "Artifact"
  def humanName     : String    = "File"
  def tpe           : Obj.Type  = Artifact
  def category      : String    = ObjView.categResources
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Artifact[S])(implicit tx: S#Tx): ArtifactObjView[S] with ListObjView[S] = {
    val peer      = obj
    val value     = peer.value  // peer.child.path
    val editable  = false // XXX TODO -- peer.modifiableOption.isDefined
    new Impl[S](tx.newHandle(obj), value, isEditable = editable).init(obj)
  }

  type LocationConfig[S <: stm.Sys[S]] = Either[stm.Source[S#Tx, ArtifactLocation[S]], (String, File)]
  final case class Config[S <: stm.Sys[S]](name: String, file: File, location: LocationConfig[S])

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = {
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
      prepare = Some(ggFile.value))
    res.foreach { case (name, f) =>
      ActionArtifactLocation.query[S](workspace.rootH, file = f, window = window).foreach { location =>
        val res1 = Config(name = name, file = f, location = location)
        ok(res1)
      }
    }
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val (list0, loc) = location match {
      case Left(source) => (Nil, source())
      case Right((nameLoc, directory)) =>
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