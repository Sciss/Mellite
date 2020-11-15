/*
 *  NoMakeListObjViewFactory.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.desktop
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.Obj
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.ObjListView
import de.sciss.proc.Universe

import scala.util.Failure

/** A utility trait for `ListObjView.Factory` that assumes the object cannot
  * be constructed by the user. It implements `initMakeDialog` and `initMakeCmdLine`
  * by throwing an `UnsupportedOperationException`.
  */
trait NoMakeListObjViewFactory extends ObjListView.Factory {
  override def canMakeObj: Boolean = false

  type Config[T <: LTxn[T]] = Unit

  override def makeObj[T <: Txn[T]](config: Unit)(implicit tx: T): List[Obj[T]] = Nil

  override def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                          (implicit universe: Universe[T]): Unit =
    done(initMakeCmdLine(Nil))

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] =
    Failure(new UnsupportedOperationException(s"Make $humanName"))
}
