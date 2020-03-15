/*
 *  ArtifactLocationObjViewImpl.scala
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

import java.awt.datatransfer.Transferable

import de.sciss.desktop
import de.sciss.desktop.{Desktop, FileDialog, PathField, UndoManager}
import de.sciss.file.{File, file}
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ArtifactLocationObjView.{Config, MakeResult, humanName}
import de.sciss.mellite.GUI.iconNormal
import de.sciss.mellite.edit.EditArtifactLocation
import de.sciss.mellite.impl.{ObjViewCmdLineParser, WindowImpl}
import de.sciss.mellite.{ActionArtifactLocation, ArtifactLocationObjView, DragAndDrop, GUI, ObjListView, ObjView, UniverseView}
import de.sciss.synth.proc
import de.sciss.synth.proc.Universe
import javax.swing.undo.UndoableEdit

import scala.swing.event.ValueChanged
import scala.swing.{Action, Button, Component, FlowPanel}

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

  private final class ViewImpl[S <: Sys[S]](objH: stm.Source[S#Tx, ArtifactLocation[S]], val editable: Boolean)
                                           (implicit val universe: Universe[S],
                                            val undoManager: UndoManager)
    extends UniverseView[S] with View.Editable[S] with ComponentHolder[Component] {

    type C = Component

    private[this] var ggPath      : PathField   = _
    private[this] var observer    : Disposable[S#Tx]  = _

    def init(obj0: ArtifactLocation[S])(implicit tx: S#Tx): this.type = {
      val value0 = obj0.value
      deferTx(guiInit(value0))
      observer = obj0.changed.react { implicit tx => upd =>
        deferTx {
          ggPath.value = upd.now
        }
      }
      this
    }

    private def guiInit(value0: File): Unit = {
      val _ggPath      = new PathField
      _ggPath.mode     = FileDialog.Folder
      _ggPath.enabled  = editable
      _ggPath.value    = value0
      ggPath = _ggPath

      val ggReveal: Button = new Button(Action(null)(Desktop.revealFile(_ggPath.value))) {
        icon      = iconNormal(raphael.Shapes.Inbox)
        tooltip   = s"Reveal in ${if (Desktop.isMac) "Finder" else "File Manager"}"
      }

      val p = new FlowPanel(ggReveal, _ggPath)

      if (editable) _ggPath.reactions += {
        case ValueChanged(_) => save()
      }

      component = p
    }

    def save(): Unit = {
      requireEDT()
      val newValue = ggPath.value
      val editOpt = cursor.step { implicit tx =>
        val title = s"Edit $humanName"
        objH() match {
          case ArtifactLocation.Var(pVr) =>
            val oldVal  = pVr.value
            import de.sciss.equal.Implicits._
            if (newValue === oldVal) None else {
              val pVal    = ArtifactLocation.newConst[S](newValue)
              val edit    = EditVar.Expr[S, File, ArtifactLocation](title, pVr, pVal)
              Some(edit)
            }

          case _ => None
        }
      }
      editOpt.foreach { edit =>
        undoManager.add(edit)
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = observer.dispose()
  }

  private final class FrameImpl[S <: Sys[S]](val view: ViewImpl[S], name: CellView[S#Tx, String])
    extends WindowImpl[S](name.map(n => s"$n : $humanName"))

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, ArtifactLocation[S]],
                                var directory: File, val isListCellEditable: Boolean)
    extends ArtifactLocationObjView[S]
      with ObjListView /* .ArtifactLocation */[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.StringRenderer {

    override def obj(implicit tx: S#Tx): ArtifactLocation[S] = objH()

    type E[~ <: stm.Sys[~]] = ArtifactLocation[~]

    def factory: ObjView.Factory = ArtifactLocationObjView

    def value: File = directory

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      implicit val undo: UndoManager = UndoManager()
      val _obj      = obj
      val view      = new ViewImpl[S](objH, editable = isListCellEditable).init(_obj)
      val nameView  = CellView.name(_obj)
      val fr        = new FrameImpl[S](view, nameView).init()
      Some(fr)
    }

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