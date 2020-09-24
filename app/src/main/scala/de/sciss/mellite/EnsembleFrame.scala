///*
// *  EnsembleFrame.scala
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
//import de.sciss.lucre
//import de.sciss.lucre.synth.Sys
//import de.sciss.mellite.impl.document.{EnsembleFrameImpl => Impl}
//import de.sciss.synth.proc.{Ensemble, Universe}
//
//object EnsembleFrame {
//  /** Creates a new frame for an ensemble view. */
//  def apply[T <: Txn[T]](ensemble: Ensemble[T])
//                        (implicit tx: T, universe: Universe[T]): EnsembleFrame[T] =
//    Impl(ensemble)
//}
//
//trait EnsembleFrame[T <: Txn[T]] extends lucre.swing.Window[T] {
//  def ensembleView: EnsembleView[T]
//}