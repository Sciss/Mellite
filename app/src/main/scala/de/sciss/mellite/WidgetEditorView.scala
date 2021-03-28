/*
 *  WidgetView.scala
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
import de.sciss.lucre.{Txn, synth}
import de.sciss.mellite.impl.widget.WidgetEditorViewImpl
import de.sciss.model.Model
import de.sciss.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}

object WidgetEditorView {
  def apply[T <: synth.Txn[T]](obj: Widget[T], showEditor: Boolean = true, bottom: ISeq[View[T]] = Nil)
                         (implicit tx: T, universe: Universe[T],
                          undoManager: UndoManager): WidgetEditorView[T] =
    WidgetEditorViewImpl[T](obj, showEditor = showEditor, bottom = bottom)

  sealed trait Update
  final case class DirtyChange(value: Boolean ) extends Update
  final case class TabChange  (value: Tab     ) extends Update

  sealed trait Tab
  final case object EditorTab   extends Tab
  final case object RendererTab extends Tab
}
trait WidgetEditorView[T <: Txn[T]] extends UniverseObjView[T] with Model[WidgetEditorView.Update] {
  def codeView: CodeView[T, Widget.Graph]

  def renderer: WidgetRenderView[T]

  def currentTab: WidgetEditorView.Tab

  override def obj(implicit tx: T): Widget[T]
}