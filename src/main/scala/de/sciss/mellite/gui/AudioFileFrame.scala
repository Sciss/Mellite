/*
 *  AudioFileFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package mellite
package gui

import de.sciss.synth.proc.AuralSystem
import lucre.stm
import stm.Disposable
import impl.audiofile.{FrameImpl => Impl}
import de.sciss.lucre.synth.Sys

object AudioFileFrame {
  def apply[S <: Sys[S]](doc: Document[S], elem: Element.AudioGrapheme[S])
                        (implicit tx: S#Tx, cursor: stm.Cursor[S], aural: AuralSystem): AudioFileFrame[S] =
    Impl(doc, elem)
}

trait AudioFileFrame[S <: Sys[S]] extends Disposable[S#Tx] {
  def component: desktop.Window
  def document : Document[S]
}
