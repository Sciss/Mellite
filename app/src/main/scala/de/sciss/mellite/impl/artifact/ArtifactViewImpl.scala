/*
 *  ArtifactViewImpl.scala
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

package de.sciss.mellite.impl.artifact

import de.sciss.desktop.{Desktop, FileDialog, PathField, UndoManager}
import de.sciss.file.File
import de.sciss.icons.raphael
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Disposable
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.GUI.iconNormal
import de.sciss.mellite.impl.objview.ArtifactObjView.humanName
import de.sciss.mellite.{ArtifactLocationFrame, ArtifactLocationObjView, ArtifactView}
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Universe
import javax.swing.undo.{AbstractUndoableEdit, CannotRedoException, CannotUndoException}

import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Action, Button, Component, FlowPanel}

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

  def apply[S <: Sys[S]](obj: Artifact[S], mode: Boolean, initMode: FileDialog.Mode)
                        (implicit tx: S#Tx, universe: Universe[S], undo: UndoManager): ArtifactView[S] = {
    val objH      = tx.newHandle(obj)
    val editable  = obj.modifiableOption.isDefined
    val res       = new Impl(objH, mode = mode, initMode = initMode, editable = editable)
    res.init(obj)
    res
  }

  @deprecated("should change to txn based undo-manager", "2.44.0")
  private final class UpdateChild[S <: Sys[S]](name: String, aH: stm.Source[S#Tx, Artifact.Modifiable[S]],
                                               oldChild: Artifact.Child,
                                               newChild: Artifact.Child)
                                              (implicit cursor: stm.Cursor[S])
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

    def perform()(implicit tx: S#Tx): Unit = {
      val a = aH()
      if (a.child != oldChild) throw new CannotRedoException()
      a.child = newChild
    }

    override def getPresentationName: String = name
  }

  private final class Impl[S <: Sys[S]](objH: stm.Source[S#Tx, Artifact[S]], mode: Boolean, initMode: FileDialog.Mode,
                                        val editable: Boolean)
                                       (implicit val universe: Universe[S], val undoManager: UndoManager)
    extends ArtifactView[S] with View.Editable[S] with ComponentHolder[Component] {

    type C = Component

    private[this] var ggPath      : PathField   = _
    private[this] var observer    : Disposable[S#Tx]  = _

    def init(obj0: Artifact[S])(implicit tx: S#Tx): this.type = {
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
      val (_ggPath, p) = mkPathField(reveal = true, mode = mode, initMode = initMode)
      _ggPath.value = value0

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
      val newPath = ggPath.value
      val editOpt = cursor.step { implicit tx =>
        val title = s"Edit $humanName"
        objH().modifiableOption match {
          case Some(pVr) =>
            val oldVal = pVr.child
            val newVal = Artifact.relativize(pVr.location.value, newPath)
            import de.sciss.equal.Implicits._
            if (newVal === oldVal) None else {
              val edit = new UpdateChild[S](title, tx.newHandle(pVr), oldChild = oldVal, newChild = newVal)
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

    def dispose()(implicit tx: S#Tx): Unit = observer.dispose()
  }

}
