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
import de.sciss.lucre.artifact.ArtifactLocation
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.impl.objview.ObjViewImpl
import de.sciss.synth.proc.Universe
import javax.swing.Icon

object ArtifactLocationObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = ArtifactLocation[~] // Elem[S]
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
    def mkListView[S <: Sys[S]](obj: ArtifactLocation[S])
                               (implicit tx: S#Tx): ArtifactLocationObjView[S] with ObjListView[S]

    def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S]

    def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]]

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                   (implicit universe: Universe[S]): Unit
  }

  def mkListView[S <: Sys[S]](obj: ArtifactLocation[S])(implicit tx: S#Tx): ArtifactLocationObjView[S] with ObjListView[S] =
    companion.mkListView(obj)

  final case class Config[S <: stm.Sys[S]](name: String = prefix, directory: File, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit =
    companion.initMakeDialog(window)(done)

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] =
    companion.initMakeCmdLine(args)

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] =
    companion.makeObj(config)
}
trait ArtifactLocationObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = ArtifactLocation[S]

  def directory: File
}