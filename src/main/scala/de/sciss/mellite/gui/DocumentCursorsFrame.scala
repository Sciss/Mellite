/*
 *  DocumentCursorsFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package mellite
package gui

import impl.document.{CursorsFrameImpl => Impl}
import synth.proc
import de.sciss.lucre.swing.View
import de.sciss.synth.proc.Workspace

object DocumentCursorsFrame {
  type S = proc.Confluent
  type D = S#D
  def apply(document: Workspace.Confluent)(implicit tx: D#Tx): DocumentCursorsFrame = Impl(document)
}
trait DocumentCursorsFrame extends lucre.swing.Window[DocumentCursorsFrame.D] /* [S <: Sys[S]] */ {
//  def window: desktop.Window
//  def view: DocumentCursorsView
//  def workspace: Workspace.Confluent // Document[S]
}

trait DocumentCursorsView extends View[DocumentCursorsFrame.D] {
  def workspace: Workspace[DocumentCursorsFrame.S]
}