/*
 *  MarkdownView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.impl.{MarkdownEditorViewImpl => Impl}
import de.sciss.model.Model
import de.sciss.synth.proc.{Markdown, Workspace}

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.Action

object MarkdownEditorView {
  def apply[S <: SSys[S]](obj: Markdown[S], showEditor: Boolean = true, bottom: ISeq[View[S]] = Nil)
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         undoManager: UndoManager): MarkdownEditorView[S] =
    Impl[S](obj, showEditor = showEditor, bottom = bottom)

  sealed trait Update
  final case class DirtyChange(value: Boolean) extends Update
}
trait MarkdownEditorView[S <: Sys[S]] extends ViewHasWorkspace[S] with Model[MarkdownEditorView.Update] {
  def renderer: MarkdownRenderView[S]

  def dirty(implicit tx: S#Tx): Boolean

  def save(): Unit

  def undoAction: Action
  def redoAction: Action
}