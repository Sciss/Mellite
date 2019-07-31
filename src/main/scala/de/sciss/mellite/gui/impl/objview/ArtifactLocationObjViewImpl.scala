package de.sciss.mellite.gui.impl.objview

import java.awt.datatransfer.Transferable

import de.sciss.desktop
import de.sciss.file.{File, file}
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ArtifactLocationObjView.{Config, MakeResult}
import de.sciss.mellite.gui.edit.EditArtifactLocation
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.impl.objview.{ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.{ActionArtifactLocation, ArtifactLocationObjView, DragAndDrop, GUI, ObjListView, ObjView}
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe
import javax.swing.undo.UndoableEdit

object ArtifactLocationObjViewImpl extends ArtifactLocationObjView.Companion {
  def install(): Unit =
    ArtifactLocationObjView.peer = this

  def mkListView[S <: Sys[S]](obj: ArtifactLocation[S])(implicit tx: S#Tx): ArtifactLocationObjView[S] with ObjListView[S] = {
    val peer      = obj
    val value     = peer.directory
    val editable  = ArtifactLocation.Var.unapply(peer).isDefined // .modifiableOption.isDefined
    new Impl(tx.newHandle(obj), value, isListCellEditable = editable).init(obj)
  }

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val res0 = GUI.optionToAborted(ActionArtifactLocation.queryNew(window = window, askName = true))
    val res = res0.map { case (name, dir) => Config[S](name = name, directory = dir) }
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    object p extends ObjViewCmdLineParser[Config[S]](ArtifactLocationObjView, args) {
      val const   : Opt[Boolean]  = opt     (descr = s"Make constant instead of variable")
      val location: Opt[File]     = trailArg(descr = "Directory")
      validateFileIsDirectory(location)
    }
    p.parse(Config(name = p.name(), directory = p.location(), const = p.const()))
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = ArtifactLocation.newConst[S](directory)
    val obj   = if (const) obj0 else ArtifactLocation.newVar[S](obj0)
    import proc.Implicits._
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

    override def createTransferable(): Option[Transferable] = {
      val t = DragAndDrop.Transferable.files(value)
      Some(t)
    }

    def init(obj: ArtifactLocation[S])(implicit tx: S#Tx): this.type = {
      initAttrs(obj)
      addDisposable(obj.changed.react { implicit tx =>upd =>
        deferTx {
          directory = upd.now
        }
        fire(ObjView.Repaint(this))
      })
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