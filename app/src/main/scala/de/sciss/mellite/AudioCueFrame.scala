/*
 *  AudioCueFrame.scala
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
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.audiocue.{AudioCueFrameImpl => Impl}
import de.sciss.proc.{AudioCue, Universe}

object AudioCueFrame {
  def apply[T <: Txn[T]](obj: AudioCue.Obj[T])(implicit tx: T, universe: Universe[T]): AudioCueFrame[T] =
    Impl(obj)
}

trait AudioCueFrame[T <: Txn[T]] extends lucre.swing.Window[T] {
  def view: AudioCueView[T]
  // def document : Workspace[T]
}
