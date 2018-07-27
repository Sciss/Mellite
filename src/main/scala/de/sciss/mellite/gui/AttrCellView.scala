///*
// *  AttrCellView.scala
// *  (Mellite)
// *
// *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.mellite
//package gui
//
//import de.sciss.lucre.expr.{Expr, StringObj, Type}
//import de.sciss.lucre.stm.{Obj, Sys}
//import de.sciss.lucre.swing.CellView
//import de.sciss.mellite.gui.impl.{AttrCellViewImpl => Impl}
//import de.sciss.synth.proc.ObjKeys
//
//import scala.language.higherKinds
//import scala.reflect.ClassTag
//
//object AttrCellView {
//  def apply[S <: Sys[S], A, E[~ <: Sys[~]] <: Expr[~, A]](map: Obj.AttrMap[S], key: String)
//                                     (implicit tx: S#Tx, tpe: Type.Expr[A, E],
//                                      ct: ClassTag[E[S]]): CellView.Var[S, Option[A]] { type Repr = Option[E[S]] } = {
//    new Impl.ModImpl[S, A, E](tx.newHandle(map), key)
//  }
//
//  def name[S <: Sys[S]](obj: Obj[S])(implicit tx: S#Tx): CellView[S#Tx, String] = {
//    implicit val stringEx: Type.Expr[String, StringObj] = StringObj
//    apply[S, String, StringObj](obj.attr, ObjKeys.attrName).map(_.getOrElse("<unnamed>"))
//  }
//}