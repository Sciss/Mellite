/*
 *  CodeFrame.scala
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

package de.sciss.mellite.gui

import de.sciss.lucre
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.code.{CodeFrameImpl => Impl}
import de.sciss.synth.proc.{Action, Code, Proc, Universe}

import scala.collection.immutable.{Seq => ISeq}

object CodeFrame {
  def apply[S <: Sys[S]](obj: Code.Obj[S], bottom: ISeq[View[S]])
                        (implicit tx: S#Tx, universe: Universe[S],
                         compiler: Code.Compiler): CodeFrame[S] =
    Impl(obj, bottom = bottom)

  def proc[S <: Sys[S]](proc: Proc[S])
                        (implicit tx: S#Tx, universe: Universe[S],
                         compiler: Code.Compiler): CodeFrame[S] =
    Impl.proc(proc)

  def action[S <: Sys[S]](action: Action[S])
                         (implicit tx: S#Tx, universe: Universe[S],
                          compiler: Code.Compiler): CodeFrame[S] =
    Impl.action(action)

//  def fscape[S <: Sys[S]](fscape: FScape[S])
//                       (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
//                        compiler: Code.Compiler): CodeFrame[S] =
//    Impl.fscape(fscape)
}

trait CodeFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  def codeView: CodeView[S, _]
}
