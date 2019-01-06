/*
 *  ElementActions.scala
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

package de.sciss.mellite

import de.sciss.file._
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.expr.{DoubleObj, LongObj}
import de.sciss.lucre.stm.{Folder, Sys}
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.AudioCue

object ObjectActions {
  def mkAudioFile[S <: Sys[S]](loc: ArtifactLocation[S], f: File, spec: AudioFileSpec, offset: Long = 0L,
                               gain: Double = 1.0, const: Boolean = false, name: Option[String] = None)
                              (implicit tx: S#Tx): AudioCue.Obj[S] = {
    val offset0   = LongObj.newConst[S](offset)
    val offset1   = if (const) offset0 else LongObj.newVar[S](offset0)
    val gain0     = DoubleObj.newConst[S](gain)
    val gain1     = if (const) gain0 else DoubleObj.newVar[S](gain0)
    val artifact  = Artifact(loc, f) // loc.add(f)
    val audio     = AudioCue.Obj(artifact, spec, offset1, gain1)
    val name1     = name.getOrElse(f.base)
    audio.name    = name1
    // if (index == -1) folder.addLast(obj) else folder.insert(index, obj)
    audio
  }

  def findAudioFile[S <: Sys[S]](root: Folder[S], file: File)
                                (implicit tx: S#Tx): Option[AudioCue.Obj[S]] = {
    def loop(folder: Folder[S]): Option[AudioCue.Obj[S]] = {
      folder.iterator.flatMap {
        case objT: AudioCue.Obj[S] if objT.value.artifact == file => Some(objT)
        case objT: Folder[S] => loop(objT)
        case _ => None
      } .toList.headOption
    }

    loop(root)
  }
}