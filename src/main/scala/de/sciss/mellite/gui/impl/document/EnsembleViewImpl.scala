/*
 *  EnsembleViewImpl.scala
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

package de.sciss.mellite
package gui
package impl
package document

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{BooleanCheckBoxView, View, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.swingplus.Separator
import de.sciss.synth.proc.{Ensemble, Transport, Workspace}

import scala.swing.Swing._
import scala.swing.{BoxPanel, Component, Label, Orientation}

object EnsembleViewImpl {
  def apply[S <: Sys[S]](ensObj: Ensemble[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                              cursor: stm.Cursor[S], undoManager: UndoManager): Impl[S] = {
    val ens       = ensObj
    val folder1   = FolderEditorView[S](ens.folder)
    val playing   = BooleanCheckBoxView(ens.playing, "Playing State")
    val viewPower = PlayToggleButton(ensObj)
    new Impl[S](tx.newHandle(ensObj), viewPower, folder1, playing).init()
  }

  final class Impl[S <: Sys[S]](ensembleH: stm.Source[S#Tx, Ensemble[S]], viewPower: PlayToggleButton[S],
                                        val view: FolderEditorView[S], playing: View[S])
                                       (implicit val undoManager: UndoManager, val workspace: Workspace[S],
                                        val cursor: stm.Cursor[S])
    extends ComponentHolder[Component] with EnsembleView[S] { impl =>

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

    object actionBounce extends ActionBounceTimeline(this, ensembleH)
  }
}
