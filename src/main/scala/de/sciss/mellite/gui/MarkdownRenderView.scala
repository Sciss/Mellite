/*
 *  MarkdownRenderView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.event.Observable
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.impl.markdown.MarkdownRenderViewImpl
import de.sciss.synth.proc.{Markdown, Workspace}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownRenderView {
  def apply[S <: SSys[S]](init: Markdown[S], bottom: ISeq[View[S]] = Nil, embedded: Boolean = false)
                         (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): MarkdownRenderView[S] =
    MarkdownRenderViewImpl[S](init, bottom, embedded = embedded)

  sealed trait Update[S <: Sys[S]] { def view: MarkdownRenderView[S] }
  final case class FollowedLink[S <: Sys[S]](view: MarkdownRenderView[S], now: Markdown[S]) extends Update[S]
}
trait MarkdownRenderView[S <: Sys[S]]
  extends ViewHasWorkspace[S] with Observable[S#Tx, MarkdownRenderView.Update[S]] {

  def markdown(implicit tx: S#Tx): Markdown[S]

  def markdown_=(md: Markdown[S])(implicit tx: S#Tx): Unit

  def setInProgress(md: Markdown[S], value: String)(implicit tx: S#Tx): Unit
}
