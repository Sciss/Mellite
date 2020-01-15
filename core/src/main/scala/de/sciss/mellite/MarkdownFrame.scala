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
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownFrame {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def render[S <: Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, universe: Universe[S]): Render[S]

    def basic[S <: stm.Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, cursor: stm.Cursor[S]): Basic[S]

    def editor[S <: Sys[S]](obj: Markdown[S], bottom: ISeq[View[S]])
                           (implicit tx: S#Tx, universe: Universe[S]): Editor[S]
  }

  def render[S <: Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, universe: Universe[S]): Render[S] =
    companion.render(obj)

  def basic[S <: stm.Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, cursor: stm.Cursor[S]): Basic[S] =
    companion.basic(obj)

  def editor[S <: Sys[S]](obj: Markdown[S], bottom: ISeq[View[S]] = Nil)
                         (implicit tx: S#Tx, universe: Universe[S]): Editor[S] =
    companion.editor(obj, bottom = bottom)

  trait Basic[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
    override def view: MarkdownRenderView.Basic[S]
  }

  trait Render[S <: stm.Sys[S]] extends Basic[S] {
    override def view: MarkdownRenderView[S]
  }


  trait Editor[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
    override def view: MarkdownEditorView[S]
  }
}