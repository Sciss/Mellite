///*
// *  EnsembleViewImpl.scala
// *  (Mellite)
// *
// *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU Affero General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.mellite.impl.document
//
//import de.sciss.desktop.UndoManager
//import de.sciss.lucre.{Txn => LTxn}
//import de.sciss.lucre.swing.LucreSwing.deferTx
//import de.sciss.lucre.swing.impl.ComponentHolder
//import de.sciss.lucre.swing.{BooleanCheckBoxView, View}
//import de.sciss.lucre.synth.Txn
//import de.sciss.mellite.{ActionBounce, FolderEditorView, FolderView, RunnerToggleButton}
//import de.sciss.swingplus.Separator
//import de.sciss.synth.proc.{Ensemble, Universe}
//
//import scala.swing.Swing._
//import scala.swing.{BoxPanel, Component, Label, Orientation}
//
//object EnsembleViewImpl {
//  def apply[T <: Txn[T]](ensObj: Ensemble[T])(implicit tx: T, universe: Universe[T],
//                                              undoManager: UndoManager): Impl[T] = {
//    val ens       = ensObj
//    val folder1   = FolderEditorView[T](ens.folder)
//    import universe.cursor
//    val playing   = BooleanCheckBoxView(ens.playing, "Playing State")
//    val viewPower = RunnerToggleButton(ensObj)
//    new Impl[T](tx.newHandle(ensObj), viewPower, folder1, playing).init()
//  }
//
//  final class Impl[T <: Txn[T]](ensembleH: Source[T, Ensemble[T]], viewPower: RunnerToggleButton[T],
//                                val view: FolderEditorView[T], playing: View[T])
//    extends ComponentHolder[Component] with EnsembleView[T] { impl =>
//
//    type C = Component
//
//    implicit val universe: Universe[T] = view.universe
//
//    def undoManager: UndoManager = view.undoManager
//
//    def ensemble(implicit tx: T): Ensemble[T] = ensembleH()
//
//    def folderView: FolderView[T] = view.peer
//
////    def transport: Transport[T] = viewPower.transport
//
//    def init()(implicit tx: T): this.type = {
//      deferTx {
//        guiInit()
//      }
//      this
//    }
//
//    def guiInit(): Unit = {
//      component = new BoxPanel(Orientation.Vertical) {
//        contents += view.component
//        contents += Separator()
//        contents += VStrut(2)
//        contents += new BoxPanel(Orientation.Horizontal) {
//          contents += viewPower.component
//          contents += HStrut(16)
//          contents += new Label("Playing:")
//          contents += HStrut(4)
//          contents += playing.component
//        }
//        contents += VStrut(2)
//      }
//    }
//
//    def dispose()(implicit tx: T): Unit = {
//      viewPower .dispose()
//      view      .dispose()
//      playing   .dispose()
//    }
//
//    object actionBounce extends ActionBounce(this, ensembleH)
//  }
//}
