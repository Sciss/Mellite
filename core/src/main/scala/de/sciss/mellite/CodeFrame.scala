/*
 *  CodeFrame.scala
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

package de.sciss.mellite

import de.sciss.lucre
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.proc.{Action, Code, Control, Proc, Universe}

import scala.collection.immutable.{Seq => ISeq}

object CodeFrame {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: Txn[T]](obj: Code.Obj[T], bottom: ISeq[View[T]])
                          (implicit tx: T, universe: Universe[T],
                           compiler: Code.Compiler): CodeFrame[T]

    def proc[T <: Txn[T]](proc: Proc[T])
                         (implicit tx: T, universe: Universe[T],
                          compiler: Code.Compiler): CodeFrame[T]

    def control[T <: Txn[T]](control: Control[T])
                           (implicit tx: T, universe: Universe[T],
                            compiler: Code.Compiler): CodeFrame[T]

    def action[T <: Txn[T]](action: Action[T])
                            (implicit tx: T, universe: Universe[T],
                             compiler: Code.Compiler): CodeFrame[T]

//    def actionRaw[T <: Txn[T]](action: ActionRaw[T])
//                              (implicit tx: T, universe: Universe[T],
//                            compiler: Code.Compiler): CodeFrame[T]
  }

  def apply[T <: Txn[T]](obj: Code.Obj[T], bottom: ISeq[View[T]])
                        (implicit tx: T, universe: Universe[T],
                         compiler: Code.Compiler): CodeFrame[T] =
    companion(obj, bottom = bottom)

  def proc[T <: Txn[T]](proc: Proc[T])
                        (implicit tx: T, universe: Universe[T],
                         compiler: Code.Compiler): CodeFrame[T] =
    companion.proc(proc)

  def control[T <: Txn[T]](control: Control[T])
                          (implicit tx: T, universe: Universe[T],
                           compiler: Code.Compiler): CodeFrame[T] =
    companion.control(control)

  def action[T <: Txn[T]](action: Action[T])
                         (implicit tx: T, universe: Universe[T],
                          compiler: Code.Compiler): CodeFrame[T] =
    companion.action(action)

//  def actionRaw[T <: Txn[T]](action: ActionRaw[T])
//                            (implicit tx: T, universe: Universe[T],
//                          compiler: Code.Compiler): CodeFrame[T] =
//    companion.actionRaw(action)

//  def fscape[T <: Txn[T]](fscape: FScape[T])
//                       (implicit tx: T, workspace: Workspace[T], cursor: Cursor[T],
//                        compiler: Code.Compiler): CodeFrame[T] =
//    Impl.fscape(fscape)
}

trait CodeFrame[T <: LTxn[T]] extends lucre.swing.Window[T] {
  def codeView: CodeView[T, _]

  override def view: UniverseObjView[T]
}
