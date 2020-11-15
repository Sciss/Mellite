/*
 *  DocumentCursorsFrame.scala
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
import de.sciss.lucre.swing.View
import de.sciss.mellite.impl.document.{CursorsFrameImpl => Impl}
import de.sciss.proc
import de.sciss.proc.{Universe, Workspace}

object DocumentCursorsFrame {
  type S = proc.Confluent
  type T = proc.Confluent .Txn
  type D = proc.Durable   .Txn

  def apply(document: proc.Workspace.Confluent)(implicit tx: D, universe: Universe[T]): DocumentCursorsFrame =
    Impl(document)
}
trait DocumentCursorsFrame extends lucre.swing.Window[DocumentCursorsFrame.D] /* [T <: Txn[T]] */ {
//  def window: desktop.Window
//  def view: DocumentCursorsView
//  def workspace: Workspace.Confluent // Document[T]
}

trait DocumentCursorsView extends View[DocumentCursorsFrame.D] {
  def universe  : Universe  [DocumentCursorsFrame.T]
  def workspace : Workspace [DocumentCursorsFrame.T]
}