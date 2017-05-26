/*
 *  MarkdownFrame.scala
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

import de.sciss.lucre
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.{MarkdownFrameImpl => Impl}
import de.sciss.synth.proc.{Markdown, Workspace}

import scala.collection.immutable.{Seq => ISeq}

object MarkdownEditorFrame {
  def apply[S <: Sys[S]](obj: Markdown[S], bottom: ISeq[View[S]] = Nil)
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): MarkdownEditorFrame[S] =
    Impl.editor(obj, bottom = bottom)
}

trait MarkdownEditorFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  override def view: MarkdownEditorView[S]
}

object MarkdownRenderFrame {
  def apply[S <: Sys[S]](obj: Markdown[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                           cursor: stm.Cursor[S]): MarkdownRenderFrame[S] =
    Impl.render(obj)
}

trait MarkdownRenderFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  override def view: MarkdownRenderView[S]
}