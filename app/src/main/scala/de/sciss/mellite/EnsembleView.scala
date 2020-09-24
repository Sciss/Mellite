///*
// *  EnsembleView.scala
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
//package de.sciss.mellite
//
//import de.sciss.desktop.UndoManager
//import de.sciss.lucre.swing.View
//import de.sciss.lucre.synth.Sys
//import de.sciss.mellite.impl.document.{EnsembleViewImpl => Impl}
//import de.sciss.synth.proc.{Ensemble, Universe}
//
//object EnsembleView {
//  def apply[T <: Txn[T]](ensemble: Ensemble[T])(implicit tx: T, universe: Universe[T],
//                                                undoManager: UndoManager): EnsembleView[T] =
//    Impl(ensemble)
//}
//trait EnsembleView[T <: Txn[T]] extends View.Editable[T] with UniverseView[T] with CanBounce {
//  def folderView: FolderView[T]
//
//  def ensemble(implicit tx: T): Ensemble[T]
//
////  def transport: Transport[T]
//}