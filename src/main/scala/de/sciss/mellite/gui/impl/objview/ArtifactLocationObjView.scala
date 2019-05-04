/*
 *  ArtifactLocationObjView.scala
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

package de.sciss.mellite.gui.impl.objview

import de.sciss.desktop
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditArtifactLocation
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.{ActionArtifactLocation, GUI, ObjListView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object ArtifactLocationObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = ArtifactLocation[~] // Elem[S]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Location)
  val prefix        : String    = "ArtifactLocation"
  def humanName     : String    = "File Location"
  def tpe           : Obj.Type  = ArtifactLocation
  def category      : String    = ObjView.categResources
  def canMakeObj : Boolean   = true

  def mkListView[S <: Sys[S]](obj: ArtifactLocation[S])(implicit tx: S#Tx): ArtifactLocationObjView[S] with ObjListView[S] = {
    val peer      = obj
    val value     = peer.directory
    val editable  = ArtifactLocation.Var.unapply(peer).isDefined // .modifiableOption.isDefined
    new Impl(tx.newHandle(obj), value, isListCellEditable = editable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, directory: File, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val res0 = GUI.optionToAborted(ActionArtifactLocation.queryNew(window = window, askName = true))
    val res = res0.map { case (name, dir) => Config[S](name = name, directory = dir) }
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = Config(directory = null)
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[File]("location")
      .text("Directory")
      .required()
      .validate(dir => if (dir.isDirectory) success else failure(s"Not a directory: $dir"))
      .action((v, c) => c.copy(directory = v))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = ArtifactLocation.newConst[S](directory)
    val obj   = if (const) obj0 else ArtifactLocation.newVar[S](obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, ArtifactLocation[S]],
                                var directory: File, val isListCellEditable: Boolean)
    extends ArtifactLocationObjView[S]
    with ObjListView /* .ArtifactLocation */[S]
    with ObjViewImpl.Impl[S]
    with ObjListViewImpl.StringRenderer
    with ObjViewImpl.NonViewable[S] {

    override def obj(implicit tx: S#Tx): ArtifactLocation[S] = objH()

    type E[~ <: stm.Sys[~]] = ArtifactLocation[~]

    def factory: ObjView.Factory = ArtifactLocationObjView

    def value: File = directory

    def init(obj: ArtifactLocation[S])(implicit tx: S#Tx): this.type = {
      initAttrs(obj)
      disposables ::= obj.changed.react { implicit tx => upd =>
        deferTx {
          directory = upd.now
        }
        fire(ObjView.Repaint(this))
      }
      this
    }

    def tryEditListCell(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
      val dirOpt = value match {
        case s: String  => Some(file(s))
        case f: File    => Some(f)
        case _          => None
      }
      dirOpt.flatMap { newDir =>
        val loc = obj
        import de.sciss.equal.Implicits._
        if (loc.directory === newDir) None else ArtifactLocation.Var.unapply(loc).map { mod =>
          EditArtifactLocation(mod, newDir)
        }
      }
    }
  }
}
trait ArtifactLocationObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = ArtifactLocation[S]

  def directory: File
}