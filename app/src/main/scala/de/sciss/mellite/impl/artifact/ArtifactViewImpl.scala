/*
 *  ArtifactViewImpl.scala
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

package de.sciss.mellite.impl.artifact

import java.net.URI
import de.sciss.desktop.{Desktop, FileDialog, PathField, UndoManager}
import de.sciss.file.File
import de.sciss.icons.raphael
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Artifact, Cursor, Disposable, Source}
import de.sciss.mellite.GUI.iconNormal
import de.sciss.mellite.impl.objview.ArtifactObjView.humanName
import de.sciss.mellite.{ArtifactLocationFrame, ArtifactLocationObjView, ArtifactView, ViewState}
import de.sciss.swingplus.ComboBox
import de.sciss.proc.Universe

import javax.swing.undo.{AbstractUndoableEdit, CannotRedoException, CannotUndoException}
import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Action, Button, Component, FlowPanel}
import scala.util.Try

object ArtifactViewImpl {
  def mkPathField(reveal: Boolean, mode: Boolean,
                  initMode: FileDialog.Mode = FileDialog.Save): (PathField, FlowPanel) = {
    val ggFile  = new PathField
    ggFile.mode = initMode

    val c0: List[Component] = if (!mode) ggFile :: Nil else {
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
      ggFile :: ggMode :: Nil
    }

    val c = if (!reveal) c0 else {
      val ggReveal = new Button(Action(null)(Desktop.revealFile(ggFile.value)))
      ggReveal.icon      = iconNormal(raphael.Shapes.Inbox)
      ggReveal.tooltip   = s"Reveal in ${if (Desktop.isMac) "Finder" else "File Manager"}"
      ggReveal :: c0
    }

    val ggValue = new FlowPanel(c: _*)
    (ggFile, ggValue)
  }

  def apply[T <: Txn[T]](obj: Artifact[T], mode: Boolean, initMode: FileDialog.Mode)
                        (implicit tx: T, universe: Universe[T], undo: UndoManager): ArtifactView[T] = {
    val objH      = tx.newHandle(obj)
    val editable  = obj.modifiableOption.isDefined
    val res       = new Impl(objH, mode = mode, initMode = initMode, editable = editable)
    res.init(obj)
    res
  }

  @deprecated("should change to txn based undo-manager", "2.44.0")
  private final class UpdateChild[T <: Txn[T]](name: String, aH: Source[T, Artifact.Modifiable[T]],
                                               oldChild: Artifact.Child,
                                               newChild: Artifact.Child)
                                              (implicit cursor: Cursor[T])
    extends AbstractUndoableEdit {

    override def undo(): Unit = {
      super.undo()
      cursor.step { implicit tx =>
        val a = aH()
        if (a.child != newChild) throw new CannotUndoException()
        a.child = oldChild
      }
    }

    override def redo(): Unit = {
      super.redo()
      cursor.step { implicit tx => perform() }
    }

    def perform()(implicit tx: T): Unit = {
      val a = aH()
      if (a.child != oldChild) throw new CannotRedoException()
      a.child = newChild
    }

    override def getPresentationName: String = name
  }

  private final class Impl[T <: Txn[T]](objH: Source[T, Artifact[T]], mode: Boolean, initMode: FileDialog.Mode,
                                        val editable: Boolean)
                                       (implicit val universe: Universe[T], val undoManager: UndoManager)
    extends ArtifactView[T] with View.Editable[T] with ComponentHolder[Component] {

    type C = Component

    private[this] var ggPath      : PathField   = _
    private[this] var observer    : Disposable[T]  = _

    override def obj(implicit tx: T): Artifact[T] = objH()

    override def viewState: Set[ViewState] = Set.empty

    def init(obj0: Artifact[T])(implicit tx: T): this.type = {
      val value0 = obj0.value
      deferTx(initGUI(value0))
      observer = obj0.changed.react { implicit tx => upd =>
        deferTx {
          val fileNowOpt = Try(new File(upd.now)).toOption
          ggPath.valueOption = fileNowOpt
        }
      }
      this
    }

    private def initGUI(value0: URI): Unit = {
      val (_ggPath, p) = mkPathField(reveal = true, mode = mode, initMode = initMode)
      val file0Opt = Try(new File(value0)).toOption
      _ggPath.valueOption = file0Opt

      val ggLoc: Button = new Button(Action(null) {
        cursor.step { implicit tx =>
          ArtifactLocationFrame(objH().location)
        }
      })
      ggLoc.icon      = iconNormal(raphael.Shapes.Location)
      ggLoc.tooltip   = s"${ArtifactLocationObjView.humanName} View"
      p.contents.insert(1, ggLoc)

      ggPath = _ggPath

      if (editable) _ggPath.reactions += {
        case ValueChanged(_) => save()
      }

      component = p
    }

    def save(): Unit = {
      requireEDT()
      val newPath = ggPath.valueOption.map(_.toURI)
      val editOpt = cursor.step { implicit tx =>
        val title = s"Edit $humanName"
        objH().modifiableOption match {
          case Some(pVr) if newPath.isDefined =>
            val oldVal = pVr.child
            val newVal = Artifact.Value.relativize(pVr.location.value, newPath.get)
            import de.sciss.equal.Implicits._
            if (newVal === oldVal) None else {
              val edit = new UpdateChild[T](title, tx.newHandle(pVr), oldChild = oldVal, newChild = newVal)
              edit.perform()
              Some(edit)
            }

          case _ => None
        }
      }
      editOpt.foreach { edit =>
        undoManager.add(edit)
      }
    }

    def dispose()(implicit tx: T): Unit = observer.dispose()
  }

}
