/*
 *  CodeView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import javax.swing.undo.UndoableEdit
import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm.{Disposable, Sys, TxnLike}
import de.sciss.lucre.swing.View
import de.sciss.mellite.gui.impl.interpreter.{CodeViewImpl => Impl}
import de.sciss.model.Model
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Code, Universe}

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.Future
import scala.swing.Action

object CodeView {
  trait Handler[S <: Sys[S], In, -Out] extends Disposable[S#Tx] {
    def in(): In
    def save(in: In, out: Out)(implicit tx: S#Tx): UndoableEdit
  }

  /** If `graph` is given, the `apply` action is tied to updating the graph variable. */
  def apply[S <: Sys[S]](obj: Code.Obj[S], code0: Code, bottom: ISeq[View[S]])
                        (handler: Option[Handler[S, code0.In, code0.Out]])
                        (implicit tx: S#Tx, universe: Universe[S],
                         compiler: Code.Compiler,
                         undoManager: UndoManager): CodeView[S, code0.Out] =
    Impl[S](obj, code0, bottom = bottom)(handler)

  sealed trait Update
  case class DirtyChange(value: Boolean) extends Update
}
trait CodeView[S <: Sys[S], Out] extends UniverseView[S] with Model[CodeView.Update] {
  def isCompiling(implicit tx: TxnLike): Boolean

  def dirty(implicit tx: TxnLike): Boolean

  /** Call on EDT outside Txn */
  def save(): Future[Unit]

  def preview(): Future[Out]

  def currentText: String

  // def updateSource(text: String)(implicit tx: S#Tx): Unit

  def undoAction: Action
  def redoAction: Action
}