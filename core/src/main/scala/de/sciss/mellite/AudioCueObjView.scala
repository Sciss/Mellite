/*
 *  AudioCueObjView.scala
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

import de.sciss.desktop
import de.sciss.file._
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.objview.ObjViewImpl
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.{AudioCue, Universe}
import javax.swing.Icon

object AudioCueObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = AudioCue.Obj[~] // Grapheme.Expr.Audio[S]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Music)
  val prefix        : String    = "AudioCue"
  def humanName     : String    = "Audio File"
  def tpe           : Obj.Type  = AudioCue.Obj // ElemImpl.AudioGrapheme.typeId
  def category      : String    = ObjView.categResources
  def canMakeObj    : Boolean   = true

  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def mkListView[S <: Sys[S]](obj: AudioCue.Obj[S])
                               (implicit tx: S#Tx): AudioCueObjView[S] with ObjListView[S]

    def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S]

    def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]]

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                   (implicit universe: Universe[S]): Unit
  }

  def mkListView[S <: Sys[S]](obj: AudioCue.Obj[S])
                             (implicit tx: S#Tx): AudioCueObjView[S] with ObjListView[S] =
    companion.mkListView(obj)

  def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] =
    companion.initMakeCmdLine(args)

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] =
    companion.makeObj(config)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit =
    companion.initMakeDialog(window)(done)

  type LocationConfig[S <: stm.Sys[S]] = ActionArtifactLocation.QueryResult[S]

  final case class SingleConfig[S <: stm.Sys[S]](name: String, file: File, spec: AudioFileSpec,
                                                 location: LocationConfig[S], offset: Long = 0L, gain: Double = 1.0,
                                                 const: Boolean = false)
  type Config[S <: stm.Sys[S]] = List[SingleConfig[S]]
}
trait AudioCueObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = AudioCue.Obj[S]

  def value: AudioCue
}