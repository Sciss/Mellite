/*
 *  AudioCueFrameImpl.scala
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

package de.sciss.mellite.gui.impl.audiocue

import de.sciss.file._
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.WindowImpl
import de.sciss.mellite.gui.{AudioFileFrame, AudioCueView}
import de.sciss.synth.proc.{AudioCue, Universe}

object AudioCueFrameImpl {
  def apply[S <: Sys[S]](obj: AudioCue.Obj[S])
                        (implicit tx: S#Tx, universe: Universe[S]): AudioFileFrame[S] = {
    val afv       = AudioCueView(obj)
    val name0     = CellView.name(obj)
    val file      = obj.value.artifact
    val fileName  = file.base
    import de.sciss.equal.Implicits._
    val name      = name0.map { n =>
      if (n === fileName) n else s"$n- $fileName"
    }
    val res       = new Impl(/* doc, */ view = afv, name = name, _file = file)
    res.init()
    res
  }

  private final class Impl[S <: Sys[S]](/* val document: Workspace[S], */ val view: AudioCueView[S],
                                        name: CellView[S#Tx, String], _file: File)
    extends WindowImpl[S](name)
    with AudioFileFrame[S] {

    override protected def initGUI(): Unit = windowFile = Some(_file)
  }
}