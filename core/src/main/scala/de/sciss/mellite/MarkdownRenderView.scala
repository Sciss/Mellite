/*
 *  MarkdownRenderView.scala
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

import de.sciss.lucre.event.Observable
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.synth.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownRenderView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[S <: SSys[S]](init: Markdown[S], bottom: ISeq[View[S]], embedded: Boolean)
                           (implicit tx: S#Tx, universe: Universe[S]): MarkdownRenderView[S]

    def basic[S <: Sys[S]](init: Markdown[S], bottom: ISeq[View[S]], embedded: Boolean)
                          (implicit tx: S#Tx, cursor: stm.Cursor[S]): Basic[S]
  }

  def apply[S <: SSys[S]](init: Markdown[S], bottom: ISeq[View[S]] = Nil, embedded: Boolean = false)
                         (implicit tx: S#Tx, universe: Universe[S]): MarkdownRenderView[S] =
    companion(init, bottom, embedded = embedded)

  def basic[S <: Sys[S]](init: Markdown[S], bottom: ISeq[View[S]] = Nil, embedded: Boolean = false)
                        (implicit tx: S#Tx, cursor: stm.Cursor[S]): Basic[S] =
    companion.basic(init, bottom, embedded = embedded)

  sealed trait Update[S <: Sys[S]] { def view: Basic[S] }
  final case class FollowedLink[S <: Sys[S]](view: Basic[S], now: Markdown[S]) extends Update[S]

  trait Basic[S <: Sys[S]] extends View.Cursor[S] with Observable[S#Tx, MarkdownRenderView.Update[S]] {
    def markdown(implicit tx: S#Tx): Markdown[S]

    def markdown_=(md: Markdown[S])(implicit tx: S#Tx): Unit

    def setInProgress(md: Markdown[S], value: String)(implicit tx: S#Tx): Unit
  }
}
trait MarkdownRenderView[S <: Sys[S]]
  extends MarkdownRenderView.Basic[S] with UniverseView[S]