/*
 *  MarkdownFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.lucre
import de.sciss.lucre.{Cursor, Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.synth.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownFrame {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def render[T <: Txn[T]](obj: Markdown[T])(implicit tx: T, universe: Universe[T]): Render[T]

    def basic[T <: LTxn[T]](obj: Markdown[T])(implicit tx: T, cursor: Cursor[T]): Basic[T]

    def editor[T <: Txn[T]](obj: Markdown[T], bottom: ISeq[View[T]])
                           (implicit tx: T, universe: Universe[T]): Editor[T]
  }

  def render[T <: Txn[T]](obj: Markdown[T])(implicit tx: T, universe: Universe[T]): Render[T] =
    companion.render(obj)

  def basic[T <: LTxn[T]](obj: Markdown[T])(implicit tx: T, cursor: Cursor[T]): Basic[T] =
    companion.basic(obj)

  def editor[T <: Txn[T]](obj: Markdown[T], bottom: ISeq[View[T]] = Nil)
                         (implicit tx: T, universe: Universe[T]): Editor[T] =
    companion.editor(obj, bottom = bottom)

  trait Basic[T <: LTxn[T]] extends lucre.swing.Window[T] {
    override def view: MarkdownRenderView.Basic[T]
  }

  trait Render[T <: LTxn[T]] extends Basic[T] {
    override def view: MarkdownRenderView[T]
  }


  trait Editor[T <: LTxn[T]] extends lucre.swing.Window[T] {
    override def view: MarkdownEditorView[T]
  }
}