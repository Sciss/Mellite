/*
 *  NoArgsListObjViewFactory.scala
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
import de.sciss.desktop.OptionPane
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.{GUI, ObjListView}
import de.sciss.synth.proc.Universe

/** A utility trait for `ListObjView.Factory` that assumes the object is
  * constructor without further arguments. It implements `initMakeDialog` by
  * prompting for a name, and `initMakeCmdLine` by simply allowing for the
  * naming of the object.
  */
trait NoArgsListObjViewFactory extends ObjListView.Factory {
  def canMakeObj  : Boolean   = true

  type Config[T <: LTxn[T]] = String

  override def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])(done: MakeResult[T] => Unit)
                                          (implicit universe: Universe[T]): Unit = {
    val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
      messageType = OptionPane.Message.Question, initial = prefix)
    opt.title = s"New $prefix"
    val res = GUI.optionToAborted(opt.show(window))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args)
    p.parse(p.name())
  }
}
