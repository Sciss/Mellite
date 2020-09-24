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
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Txn => LTxn}
import de.sciss.mellite.impl.objview.ObjViewImpl
import de.sciss.synth.io.AudioFileSpec
import de.sciss.synth.proc.{AudioCue, Universe}
import javax.swing.Icon

object AudioCueObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = AudioCue.Obj[~] // Grapheme.Expr.Audio[T]
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
    def mkListView[T <: Txn[T]](obj: AudioCue.Obj[T])
                               (implicit tx: T): AudioCueObjView[T] with ObjListView[T]

    def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T]

    def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]]

    def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                   (implicit universe: Universe[T]): Unit
  }

  def mkListView[T <: Txn[T]](obj: AudioCue.Obj[T])
                             (implicit tx: T): AudioCueObjView[T] with ObjListView[T] =
    companion.mkListView(obj)

  def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] =
    companion.initMakeCmdLine(args)

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] =
    companion.makeObj(config)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit =
    companion.initMakeDialog(window)(done)

  type LocationConfig[T <: LTxn[T]] = ActionArtifactLocation.QueryResult[T]

  final case class SingleConfig[T <: LTxn[T]](name: String, file: File, spec: AudioFileSpec,
                                              location: LocationConfig[T], offset: Long = 0L, gain: Double = 1.0,
                                              const: Boolean = false)
  type Config[T <: LTxn[T]] = List[SingleConfig[T]]
}
trait AudioCueObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = AudioCue.Obj[T]

  def value: AudioCue
}