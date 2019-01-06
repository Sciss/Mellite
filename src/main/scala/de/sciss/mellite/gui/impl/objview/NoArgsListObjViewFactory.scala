/*
 *  NoArgsListObjViewFactory.scala
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
import de.sciss.desktop.OptionPane
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.{GUI, ListObjView}
import de.sciss.synth.proc.Universe

/** A utility trait for `ListObjView.Factory` that assumes the object is
  * constructor without further arguments. It implements `initMakeDialog` by
  * prompting for a name, and `initMakeCmdLine` by simply allowing for the
  * naming of the object.
  */
trait NoArgsListObjViewFactory extends ListObjView.Factory {
  def canMakeObj  : Boolean   = true

  type Config[S <: stm.Sys[S]] = String

  override def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                          (implicit universe: Universe[S]): Unit = {
    val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
      messageType = OptionPane.Message.Question, initial = prefix)
    opt.title = s"New $prefix"
    val res = GUI.optionToAborted(opt.show(window))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = prefix
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, _) => v)
    parseConfig(args, default)
  }
}
