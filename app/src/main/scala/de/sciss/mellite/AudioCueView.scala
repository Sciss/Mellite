/*
 *  AudioCueView.scala
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

import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.audiocue.{AudioCueViewImpl => Impl}
import de.sciss.proc.{AudioCue, Universe}

object AudioCueView {
  def apply[T <: Txn[T]](obj: AudioCue.Obj[T])(implicit tx: T, universe: Universe[T]): AudioCueView[T] =
    Impl(obj)
}
trait AudioCueView[T <: Txn[T]] extends UniverseView[T] with AudioCueObjView[T]
