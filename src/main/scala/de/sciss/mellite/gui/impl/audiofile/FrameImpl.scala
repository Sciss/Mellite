/*
 *  FrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package audiofile

import de.sciss.file._
import de.sciss.lucre.stm
import de.sciss.lucre.swing.CellView
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{AudioCue, Workspace}

object FrameImpl {
  def apply[S <: Sys[S]](obj: AudioCue.Obj[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): AudioFileFrame[S] = {
    implicit val aural = Mellite.auralSystem
    val afv       = AudioFileView(obj)
    val name0     = AttrCellView.name(obj)
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

  private final class Impl[S <: Sys[S]](/* val document: Workspace[S], */ val view: AudioFileView[S],
                                        name: CellView[S#Tx, String], _file: File)
    extends WindowImpl[S](name)
    with AudioFileFrame[S] {

    override protected def initGUI(): Unit = windowFile = Some(_file)
  }
}