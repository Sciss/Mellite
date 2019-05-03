/*
 *  MarkdownFrame.scala
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

package de.sciss.mellite.gui

import de.sciss.lucre
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.markdown.MarkdownFrameImpl
import de.sciss.synth.proc.{Markdown, Universe}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownEditorFrame {
  def apply[S <: Sys[S]](obj: Markdown[S], bottom: ISeq[View[S]] = Nil)
                        (implicit tx: S#Tx, universe: Universe[S]): MarkdownEditorFrame[S] =
    MarkdownFrameImpl.editor(obj, bottom = bottom)
}

trait MarkdownEditorFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  override def view: MarkdownEditorView[S]
}

object MarkdownRenderFrame {
  def apply[S <: Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, universe: Universe[S]): MarkdownRenderFrame[S] =
    MarkdownFrameImpl.render(obj)

  def basic[S <: stm.Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, cursor: stm.Cursor[S]): Basic[S] =
    MarkdownFrameImpl.basic(obj)

  trait Basic[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
    override def view: MarkdownRenderView.Basic[S]
  }
}

trait MarkdownRenderFrame[S <: stm.Sys[S]] extends MarkdownRenderFrame.Basic[S] {
  override def view: MarkdownRenderView[S]
}