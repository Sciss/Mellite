/*
 *  ControlView.scala
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

package de.sciss.mellite

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.impl.control.ControlEditorViewImpl
import de.sciss.model.Model
import de.sciss.synth.proc.{Control, Universe}

import scala.collection.immutable.{Seq => ISeq}

object ControlEditorView {
  def apply[S <: SSys[S]](obj: Control[S], showEditor: Boolean = true, bottom: ISeq[View[S]] = Nil)
                         (implicit tx: S#Tx, universe: Universe[S],
                          undoManager: UndoManager): ControlEditorView[S] =
    ControlEditorViewImpl[S](obj, showEditor = showEditor, bottom = bottom)

  sealed trait Update
  final case class DirtyChange(value: Boolean ) extends Update
  final case class TabChange  (value: Tab     ) extends Update

  sealed trait Tab
  final case object EditorTab   extends Tab
  final case object RendererTab extends Tab
}
trait ControlEditorView[S <: Sys[S]] extends UniverseView[S] with Model[ControlEditorView.Update] {
  def codeView: CodeView[S, Control.Graph]

//  def renderer: ControlRenderView[S]

  def control(implicit tx: S#Tx): Control[S]

//  def control_=(md: Control[S])(implicit tx: S#Tx): Unit

  def currentTab: ControlEditorView.Tab

  //  def dirty(implicit tx: TxnLike): Boolean

  //  def save(): Unit

  //  def undoAction: Action
  //  def redoAction: Action
}