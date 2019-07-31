/*
 *  MarkdownEditorView.scala
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
import de.sciss.lucre.stm.{Sys, TxnLike}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.model.Model
import de.sciss.synth.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.Action

object MarkdownEditorView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[S <: SSys[S]](obj: Markdown[S], showEditor: Boolean, bottom: ISeq[View[S]])
                           (implicit tx: S#Tx, universe: Universe[S],
                            undoManager: UndoManager): MarkdownEditorView[S]
  }

  def apply[S <: SSys[S]](obj: Markdown[S], showEditor: Boolean = true, bottom: ISeq[View[S]] = Nil)
                        (implicit tx: S#Tx, universe: Universe[S],
                         undoManager: UndoManager): MarkdownEditorView[S] =
    companion(obj, showEditor = showEditor, bottom = bottom)

  sealed trait Update
  final case class DirtyChange(value: Boolean) extends Update
}
trait MarkdownEditorView[S <: Sys[S]] extends UniverseView[S] with Model[MarkdownEditorView.Update] {
  def renderer: MarkdownRenderView[S]

  def dirty(implicit tx: TxnLike): Boolean

  def save(): Unit

  def undoAction: Action
  def redoAction: Action
}