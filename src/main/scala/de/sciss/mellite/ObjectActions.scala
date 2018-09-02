/*
 *  ElementActions.scala
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

package de.sciss.mellite

import de.sciss.file._
import de.sciss.lucre.artifact.{Artifact, ArtifactLocation}
import de.sciss.lucre.expr.{DoubleObj, LongObj}
import de.sciss.lucre.stm.{Folder, Sys}
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.AudioCue

object ObjectActions {
  def mkAudioFile[S <: Sys[S]](loc: ArtifactLocation[S], f: File, spec: AudioFileSpec)
                              (implicit tx: S#Tx): AudioCue.Obj[S] = {
    val offset    = LongObj  .newVar[S](0L )
    val gain      = DoubleObj.newVar[S](1.0)
    val artifact  = Artifact(loc, f) // loc.add(f)
    val audio     = AudioCue.Obj(artifact, spec, offset, gain)
    val name      = f.base
    audio.name    = name
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