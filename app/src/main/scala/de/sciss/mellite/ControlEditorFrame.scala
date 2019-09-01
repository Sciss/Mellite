///*
// *  ControlFrame.scala
// *  (Mellite)
// *
// *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
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
//import de.sciss.lucre.stm
//import de.sciss.lucre.swing.View
//import de.sciss.lucre.synth.Sys
//import de.sciss.mellite.impl.control.ControlFrameImpl
//import de.sciss.synth.proc.{Universe, Control}
//
//import scala.collection.immutable.{Seq => ISeq}
//
//object ControlEditorFrame {
//  def apply[S <: Sys[S]](obj: Control[S], bottom: ISeq[View[S]] = Nil)
//                        (implicit tx: S#Tx, universe: Universe[S]): ControlEditorFrame[S] =
//    ControlFrameImpl.editor(obj, bottom = bottom)
//}
//
//trait ControlEditorFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
//  override def view: ControlEditorView[S]
//}
//
////object ControlRenderFrame {
////  def apply[S <: Sys[S]](obj: Control[S])(implicit tx: S#Tx, universe: Universe[S]): ControlRenderFrame[S] =
////    ControlFrameImpl.render(obj)
////}
////
////trait ControlRenderFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
////  override def view: ControlRenderView[S]
////}
