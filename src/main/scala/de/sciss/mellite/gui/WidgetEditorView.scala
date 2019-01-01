/*
 *  WidgetView.scala
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
package gui

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.impl.widget.WidgetEditorViewImpl
import de.sciss.model.Model
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}

object WidgetEditorView {
  def apply[S <: SSys[S]](obj: Widget[S], showEditor: Boolean = true, bottom: ISeq[View[S]] = Nil)
                         (implicit tx: S#Tx, universe: Universe[S],
                          undoManager: UndoManager): WidgetEditorView[S] =
    WidgetEditorViewImpl[S](obj, showEditor = showEditor, bottom = bottom)

  sealed trait Update
  final case class DirtyChange(value: Boolean) extends Update
}
trait WidgetEditorView[S <: Sys[S]] extends UniverseView[S] with Model[WidgetEditorView.Update] {
  def codeView: CodeView[S, Widget.Graph]

  def renderer: WidgetRenderView[S]

//  def dirty(implicit tx: TxnLike): Boolean

//  def save(): Unit

//  def undoAction: Action
//  def redoAction: Action
}