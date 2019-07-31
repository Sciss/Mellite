/*
 *  EnsembleViewImpl.scala
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

package de.sciss.mellite.gui.impl.document

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{BooleanCheckBoxView, View}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{FolderEditorView, FolderView}
import de.sciss.mellite.gui.{ActionBounce, EnsembleView, PlayToggleButton}
import de.sciss.swingplus.Separator
import de.sciss.synth.proc.{Ensemble, Transport, Universe}

import scala.swing.Swing._
import scala.swing.{BoxPanel, Component, Label, Orientation}

object EnsembleViewImpl {
  def apply[S <: Sys[S]](ensObj: Ensemble[S])(implicit tx: S#Tx, universe: Universe[S],
                                              undoManager: UndoManager): Impl[S] = {
    val ens       = ensObj
    val folder1   = FolderEditorView[S](ens.folder)
    import universe.cursor
    val playing   = BooleanCheckBoxView(ens.playing, "Playing State")
    val viewPower = PlayToggleButton(ensObj)
    new Impl[S](tx.newHandle(ensObj), viewPower, folder1, playing).init()
  }

  final class Impl[S <: Sys[S]](ensembleH: stm.Source[S#Tx, Ensemble[S]], viewPower: PlayToggleButton[S],
                                        val view: FolderEditorView[S], playing: View[S])
    extends ComponentHolder[Component] with EnsembleView[S] { impl =>

    type C = Component

    implicit val universe: Universe[S] = view.universe

    def undoManager: UndoManager = view.undoManager

    def ensemble(implicit tx: S#Tx): Ensemble[S] = ensembleH()

    def folderView: FolderView[S] = view.peer

    def transport: Transport[S] = viewPower.transport

    def init()(implicit tx: S#Tx): this.type = {
      deferTx {
        guiInit()
      }
      this
    }

    def guiInit(): Unit = {
      component = new BoxPanel(Orientation.Vertical) {
        contents += view.component
        contents += Separator()
        contents += VStrut(2)
        contents += new BoxPanel(Orientation.Horizontal) {
          contents += viewPower.component
          contents += HStrut(16)
          contents += new Label("Playing:")
          contents += HStrut(4)
          contents += playing.component
        }
        contents += VStrut(2)
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      viewPower .dispose()
      view      .dispose()
      playing   .dispose()
    }

    object actionBounce extends ActionBounce(this, ensembleH)
  }
}
