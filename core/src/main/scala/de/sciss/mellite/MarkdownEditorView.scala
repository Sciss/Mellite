/*
 *  MarkdownEditorView.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.swing.View
import de.sciss.lucre.{Txn, TxnLike, synth}
import de.sciss.model.Model
import de.sciss.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.Action

object MarkdownEditorView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: synth.Txn[T]](obj: Markdown[T], showEditor: Boolean, bottom: ISeq[View[T]])
                                (implicit tx: T, universe: Universe[T],
                            undoManager: UndoManager): MarkdownEditorView[T]
  }

  def apply[T <: synth.Txn[T]](obj: Markdown[T], showEditor: Boolean = true, bottom: ISeq[View[T]] = Nil)
                              (implicit tx: T, universe: Universe[T],
                         undoManager: UndoManager): MarkdownEditorView[T] =
    companion(obj, showEditor = showEditor, bottom = bottom)

  sealed trait Update
  final case class DirtyChange(value: Boolean) extends Update
}
trait MarkdownEditorView[T <: Txn[T]] extends UniverseView[T] with Model[MarkdownEditorView.Update] {
  def renderer: MarkdownRenderView[T]

  def dirty(implicit tx: TxnLike): Boolean

  def save(): Unit

  def undoAction: Action
  def redoAction: Action
}