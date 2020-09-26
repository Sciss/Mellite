/*
 *  WidgetFrame.scala
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
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.widget.WidgetFrameImpl
import de.sciss.synth.proc.{Universe, Widget}

import scala.collection.immutable.{Seq => ISeq}

object WidgetEditorFrame {
  def apply[T <: Txn[T]](obj: Widget[T], bottom: ISeq[View[T]] = Nil)
                        (implicit tx: T, universe: Universe[T]): WidgetEditorFrame[T] =
    WidgetFrameImpl.editor(obj, bottom = bottom)
}

trait WidgetEditorFrame[T <: LTxn[T]] extends lucre.swing.Window[T] {
  override def view: WidgetEditorView[T]
}

object WidgetRenderFrame {
  def apply[T <: Txn[T]](obj: Widget[T])(implicit tx: T, universe: Universe[T]): WidgetRenderFrame[T] =
    WidgetFrameImpl.render(obj)
}

trait WidgetRenderFrame[T <: LTxn[T]] extends lucre.swing.Window[T] {
  override def view: WidgetRenderView[T]
}