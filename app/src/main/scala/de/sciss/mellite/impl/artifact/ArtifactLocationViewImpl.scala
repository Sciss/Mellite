/*
 *  ArtifactLocationViewImpl.scala
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
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{ArtifactLocation, Disposable, Source}
import de.sciss.mellite.ArtifactLocationObjView.humanName
import de.sciss.mellite.ArtifactLocationView
import de.sciss.mellite.GUI.iconNormal
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Universe

import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Action, Button, Component, FlowPanel}

object ArtifactLocationViewImpl {
  def mkPathField(reveal: Boolean, mode: Boolean,
                  initMode: FileDialog.Mode = FileDialog.Save): (PathField, Component) = {
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
      val ggReveal: Button = new Button(Action(null)(Desktop.revealFile(ggFile.value))) {
        icon      = iconNormal(raphael.Shapes.Inbox)
        tooltip   = s"Reveal in ${if (Desktop.isMac) "Finder" else "File Manager"}"
      }
      ggReveal :: c0
    }

    val ggValue = new FlowPanel(c: _*)
    (ggFile, ggValue)
  }

  def apply[T <: Txn[T]](obj: ArtifactLocation[T])
                        (implicit tx: T, universe: Universe[T], undo: UndoManager): ArtifactLocationView[T] = {
    val objH      = tx.newHandle(obj)
    val editable  = ArtifactLocation.Var.unapply(obj).isDefined
    val res       = new Impl(objH, editable = editable)
    res.init(obj)
    res
  }

  private final class Impl[T <: Txn[T]](objH: Source[T, ArtifactLocation[T]], val editable: Boolean)
                                           (implicit val universe: Universe[T],
                                            val undoManager: UndoManager)
    extends ArtifactLocationView[T] with ComponentHolder[Component] {

    type C = Component

    private[this] var ggPath      : PathField   = _
    private[this] var observer    : Disposable[T]  = _

    def init(obj0: ArtifactLocation[T])(implicit tx: T): this.type = {
      val value0 = obj0.value
      deferTx(guiInit(value0))
      observer = obj0.changed.react { implicit tx => upd =>
        deferTx {
          ggPath.value = upd.now
        }
      }
      this
    }

    private def value: File = {
      requireEDT()
      ggPath.value
    }

    private def guiInit(value0: File): Unit = {
      val _ggPath      = new PathField
      _ggPath.mode     = FileDialog.Folder
      _ggPath.enabled  = editable
      _ggPath.value    = value0
      ggPath = _ggPath

      val ggReveal: Button = new Button(Action(null)(Desktop.revealFile(value))) {
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
      val newValue = value
      val editOpt = cursor.step { implicit tx =>
        val title = s"Edit $humanName"
        objH() match {
          case ArtifactLocation.Var(pVr) =>
            val oldVal  = pVr.value
            import de.sciss.equal.Implicits._
            if (newValue === oldVal) None else {
              val pVal    = ArtifactLocation.newConst[T](newValue)
              val edit    = EditVar.Expr[T, File, ArtifactLocation](title, pVr, pVal)
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
