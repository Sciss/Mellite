/*
 *  ElementActions.scala
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

import de.sciss.file._
import de.sciss.lucre.{Artifact, ArtifactLocation}
import de.sciss.lucre.{DoubleObj, LongObj}
import de.sciss.lucre.{Folder, Txn}
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.AudioCue
import de.sciss.synth.proc.Implicits._

object ObjectActions {
  def mkAudioFile[T <: Txn[T]](loc: ArtifactLocation[T], f: File, spec: AudioFileSpec, offset: Long = 0L,
                               gain: Double = 1.0, const: Boolean = false, name: Option[String] = None)
                              (implicit tx: T): AudioCue.Obj[T] = {
    val offset0   = LongObj.newConst[T](offset)
    val offset1   = if (const) offset0 else LongObj.newVar[T](offset0)
    val gain0     = DoubleObj.newConst[T](gain)
    val gain1     = if (const) gain0 else DoubleObj.newVar[T](gain0)
    val artifact  = Artifact(loc, f) // loc.add(f)
    val audio     = AudioCue.Obj(artifact, spec, offset1, gain1)
    val name1     = name.getOrElse(f.base)
    audio.name    = name1
    // if (index == -1) folder.addLast(obj) else folder.insert(index, obj)
    audio
  }

  def findAudioFile[T <: Txn[T]](root: Folder[T], file: File)
                                (implicit tx: T): Option[AudioCue.Obj[T]] = {
    def loop(folder: Folder[T]): Option[AudioCue.Obj[T]] = {
      folder.iterator.flatMap {
        case objT: AudioCue.Obj[T] if objT.value.artifact == file => Some(objT)
        case objT: Folder[T] => loop(objT)
        case _ => None
      } .toList.headOption
    }

    loop(root)
  }
}