/*
 *  ArtifactLocationObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.gui.impl.artifact

import javax.swing.Icon
import javax.swing.undo.UndoableEdit
import de.sciss.desktop
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing._
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditArtifactLocation
import de.sciss.mellite.gui.impl.{ListObjViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.{ActionArtifactLocation, ListObjView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe

object ArtifactLocationObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = ArtifactLocation[~] // Elem[S]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Location)
  val prefix        : String    = "ArtifactLocation"
  def humanName     : String    = "File Location"
  def tpe           : Obj.Type  = ArtifactLocation
  def category      : String    = ObjView.categResources
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: ArtifactLocation[S])(implicit tx: S#Tx): ArtifactLocationObjView[S] with ListObjView[S] = {
    val peer      = obj
    val value     = peer.directory
    val editable  = ArtifactLocation.Var.unapply(peer).isDefined // .modifiableOption.isDefined
    new Impl(tx.newHandle(obj), value, isEditable = editable).init(obj)
  }

  type Config[S <: stm.Sys[S]] = ObjViewImpl.PrimitiveConfig[File]

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit universe: Universe[S]): Unit =
    ActionArtifactLocation.queryNew(window = window).foreach(ok(_))

  def makeObj[S <: Sys[S]](config: (String, File))(implicit tx: S#Tx): List[Obj[S]] = {
    val (name, directory) = config
    val obj  = ArtifactLocation.newVar[S](directory)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, ArtifactLocation[S]],
                                var directory: File, val isEditable: Boolean)
    extends ArtifactLocationObjView[S]
    with ListObjView /* .ArtifactLocation */[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.StringRenderer
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

    def tryEdit(value: Any)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
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
  override def obj(implicit tx: S#Tx): ArtifactLocation[S]

  def objH: stm.Source[S#Tx, ArtifactLocation[S]]

  def directory: File
}