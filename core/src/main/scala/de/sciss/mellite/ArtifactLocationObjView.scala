/*
 *  ArtifactLocationObjView.scala
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
import de.sciss.lucre.ArtifactLocation
import de.sciss.lucre.Obj
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.objview.ObjViewImpl
import de.sciss.synth.proc.Universe
import javax.swing.Icon

object ArtifactLocationObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = ArtifactLocation[~] // Elem[T]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Location)
  val prefix        : String    = "ArtifactLocation"
  def humanName     : String    = "File Location"
  def tpe           : Obj.Type  = ArtifactLocation
  def category      : String    = ObjView.categResources
  def canMakeObj : Boolean   = true

  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def mkListView[T <: Txn[T]](obj: ArtifactLocation[T])
                               (implicit tx: T): ArtifactLocationObjView[T] with ObjListView[T]

    def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T]

    def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]]

    def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                   (implicit universe: Universe[T]): Unit
  }

  def mkListView[T <: Txn[T]](obj: ArtifactLocation[T])(implicit tx: T): ArtifactLocationObjView[T] with ObjListView[T] =
    companion.mkListView(obj)

  final case class Config[T <: LTxn[T]](name: String = prefix, directory: File, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit =
    companion.initMakeDialog(window)(done)

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] =
    companion.initMakeCmdLine(args)

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] =
    companion.makeObj(config)
}
trait ArtifactLocationObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = ArtifactLocation[T]

  def directory: File
}