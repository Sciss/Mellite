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
import de.sciss.synth.proc
import de.sciss.synth.proc.{Universe, Workspace}

object DocumentCursorsFrame {
  type S = proc.Confluent
  type D = S#D

  def apply(document: Workspace.Confluent)(implicit tx: D#Tx, universe: Universe[S]): DocumentCursorsFrame =
    Impl(document)
}
trait DocumentCursorsFrame extends lucre.swing.Window[DocumentCursorsFrame.D] /* [S <: Sys[S]] */ {
//  def window: desktop.Window
//  def view: DocumentCursorsView
//  def workspace: Workspace.Confluent // Document[S]
}

trait DocumentCursorsView extends View[DocumentCursorsFrame.D] {
  def universe  : Universe  [DocumentCursorsFrame.S]
  def workspace : Workspace [DocumentCursorsFrame.S]
}