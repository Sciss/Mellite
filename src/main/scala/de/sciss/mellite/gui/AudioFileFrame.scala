/*
 *  AudioFileFrame.scala
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

package de.sciss
package mellite
package gui

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.audiocue.{FrameImpl => Impl}
import de.sciss.synth.proc.{AudioCue, Universe}

object AudioFileFrame {
  def apply[S <: Sys[S]](obj: AudioCue.Obj[S])(implicit tx: S#Tx, universe: Universe[S]): AudioFileFrame[S] =
    Impl(obj)
}

trait AudioFileFrame[S <: Sys[S]] extends lucre.swing.Window[S] {
  def view: AudioFileView[S]
  // def document : Workspace[S]
}
