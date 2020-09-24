/*
 *  AudioCueFrameImpl.scala
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

package de.sciss.mellite.impl.audiocue

import de.sciss.file._
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{AudioCueFrame, AudioCueView}
import de.sciss.synth.proc.{AudioCue, Universe}

object AudioCueFrameImpl {
  def apply[T <: Txn[T]](obj: AudioCue.Obj[T])
                        (implicit tx: T, universe: Universe[T]): AudioCueFrame[T] = {
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

  private final class Impl[T <: Txn[T]](/* val document: Workspace[T], */ val view: AudioCueView[T],
                                        name: CellView[T, String], _file: File)
    extends WindowImpl[T](name)
    with AudioCueFrame[T] {

    override protected def initGUI(): Unit = windowFile = Some(_file)
  }
}