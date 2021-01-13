/*
 *  ArtifactLocationObjViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.objview

import java.awt.datatransfer.Transferable
import java.io.File
import java.net.URI

import de.sciss.desktop
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{ArtifactLocation, Cursor, Obj, Source, Txn => LTxn}
import de.sciss.mellite.ArtifactLocationObjView.{Config, MakeResult}
import de.sciss.mellite.edit.EditArtifactLocation
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.{ActionArtifactLocation, ArtifactLocationFrame, ArtifactLocationObjView, DragAndDrop, GUI, ObjListView, ObjView}
import de.sciss.proc
import de.sciss.proc.Universe
import javax.swing.undo.UndoableEdit

import scala.util.Try

object ArtifactLocationObjViewImpl extends ArtifactLocationObjView.Companion {
  def install(): Unit =
    ArtifactLocationObjView.peer = this

  def mkListView[T <: Txn[T]](obj: ArtifactLocation[T])(implicit tx: T): ArtifactLocationObjView[T] with ObjListView[T] = {
    val peer      = obj
    val value     = peer.directory
    val editable  = ArtifactLocation.Var.unapply(peer).isDefined // .modifiableOption.isDefined
    new Impl(tx.newHandle(obj), value, isListCellEditable = editable).init(obj)
  }

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val res0 = GUI.optionToAborted(ActionArtifactLocation.queryNew(window = window, askName = true))
    val res = res0.map { case (name, dir) => Config[T](name = name, directory = dir) }
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](ArtifactLocationObjView, args) {
      val const   : Opt[Boolean]  = opt     (descr = s"Make constant instead of variable")
      val location: Opt[File]     = trailArg(descr = "Directory")
      validateFileIsDirectory(location)
    }
    p.parse(Config(name = p.name(), directory = p.location().toURI, const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = ArtifactLocation.newConst[T](directory)
    val obj   = if (const) obj0 else ArtifactLocation.newVar[T](obj0)
    import proc.Implicits._
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, ArtifactLocation[T]],
                                var directory: URI, val isListCellEditable: Boolean)
    extends ArtifactLocationObjView[T]
      with ObjListView /* .ArtifactLocation */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.StringRenderer {

    override def obj(implicit tx: T): ArtifactLocation[T] = objH()

    type E[~ <: LTxn[~]] = ArtifactLocation[~]

    def factory: ObjView.Factory = ArtifactLocationObjView

    def value: URI = directory

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = ArtifactLocationFrame(obj)
      Some(frame)
    }

    override def createTransferable(): Option[Transferable] = {
      val fileOpt = Try(new File(value)).toOption
      fileOpt.map { f =>
        DragAndDrop.Transferable.files(f)
      }
    }

    def init(obj: ArtifactLocation[T])(implicit tx: T): this.type = {
      initAttrs(obj)
      addDisposable(obj.changed.react { implicit tx =>upd =>
        deferTx {
          directory = upd.now
        }
        fire(ObjView.Repaint(this))
      })
      this
    }

    def tryEditListCell(value: Any)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
      val dirOpt = value match {
        case s: String  => Try(new URI(s)).toOption
        case f: URI     => Some(f)
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