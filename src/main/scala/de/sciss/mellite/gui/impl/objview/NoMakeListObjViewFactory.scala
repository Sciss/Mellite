/*
 *  NoMakeListObjViewFactory.scala
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

package de.sciss.mellite.gui.impl.objview

import de.sciss.desktop
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.ListObjView
import de.sciss.synth.proc.Universe

import scala.util.Failure

/** A utility trait for `ListObjView.Factory` that assumes the object cannot
  * be constructed by the user. It implements `initMakeDialog` and `initMakeCmdLine`
  * by throwing an `UnsupportedOperationException`.
  */
trait NoMakeListObjViewFactory extends ListObjView.Factory {
  override def canMakeObj: Boolean = false

  type Config[S <: stm.Sys[S]] = Unit

  override def makeObj[S <: Sys[S]](config: Unit)(implicit tx: S#Tx): List[Obj[S]] = Nil

  override def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                          (implicit universe: Universe[S]): Unit =
    done(initMakeCmdLine(Nil))

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] =
    Failure(new UnsupportedOperationException(s"Make $humanName"))
}
