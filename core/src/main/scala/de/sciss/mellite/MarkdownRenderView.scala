/*
 *  MarkdownRenderView.scala
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

import de.sciss.lucre.swing.View
import de.sciss.lucre.{Cursor, Observable, synth, Txn => LTxn}
import de.sciss.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownRenderView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: synth.Txn[T]](init: Markdown[T], bottom: ISeq[View[T]], embedded: Boolean)
                           (implicit tx: T, universe: Universe[T]): MarkdownRenderView[T]

    def basic[T <: LTxn[T]](init: Markdown[T], bottom: ISeq[View[T]], embedded: Boolean)
                           (implicit tx: T, cursor: Cursor[T]): Basic[T]
  }

  def apply[T <: synth.Txn[T]](init: Markdown[T], bottom: ISeq[View[T]] = Nil, embedded: Boolean = false)
                         (implicit tx: T, universe: Universe[T]): MarkdownRenderView[T] =
    companion(init, bottom, embedded = embedded)

  def basic[T <: LTxn[T]](init: Markdown[T], bottom: ISeq[View[T]] = Nil, embedded: Boolean = false)
                         (implicit tx: T, cursor: Cursor[T]): Basic[T] =
    companion.basic(init, bottom, embedded = embedded)

  sealed trait Update[T <: LTxn[T]] { def view: Basic[T] }
  final case class FollowedLink[T <: LTxn[T]](view: Basic[T], now: Markdown[T]) extends Update[T]

  trait Basic[T <: LTxn[T]] extends View.Cursor[T] with Observable[T, MarkdownRenderView.Update[T]] {
    def markdown(implicit tx: T): Markdown[T]

    def markdown_=(md: Markdown[T])(implicit tx: T): Unit

    def setInProgress(md: Markdown[T], value: String)(implicit tx: T): Unit
  }
}
trait MarkdownRenderView[T <: LTxn[T]]
  extends MarkdownRenderView.Basic[T] with UniverseView[T]
