/*
 *  AudioCueView.scala
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

package de.sciss.mellite.gui

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{AudioCueObjView, UniverseView}
import de.sciss.mellite.gui.impl.audiocue.{AudioCueViewImpl => Impl}
import de.sciss.synth.proc.{AudioCue, Universe}

object AudioCueView {
  def apply[S <: Sys[S]](obj: AudioCue.Obj[S])(implicit tx: S#Tx, universe: Universe[S]): AudioCueView[S] =
    Impl(obj)
}
trait AudioCueView[S <: Sys[S]] extends UniverseView[S] with AudioCueObjView[S]
