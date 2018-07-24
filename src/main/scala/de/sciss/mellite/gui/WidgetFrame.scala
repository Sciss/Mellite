/*
 *  WidgetFrame.scala
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

import de.sciss.lucre
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.widget.WidgetFrameImpl
import de.sciss.synth.proc.{Widget, Workspace}

import scala.collection.immutable.{Seq => ISeq}

object WidgetEditorFrame {
  def apply[S <: Sys[S]](obj: Widget[S], bottom: ISeq[View[S]] = Nil)
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): WidgetEditorFrame[S] =
    WidgetFrameImpl.editor(obj, bottom = bottom)
}

trait WidgetEditorFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  override def view: WidgetEditorView[S]
}

object WidgetRenderFrame {
  def apply[S <: Sys[S]](obj: Widget[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                           cursor: stm.Cursor[S]): WidgetRenderFrame[S] =
    WidgetFrameImpl.render(obj)
}

trait WidgetRenderFrame[S <: stm.Sys[S]] extends lucre.swing.Window[S] {
  override def view: WidgetRenderView[S]
}