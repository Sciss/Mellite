/*
 *  CodeObjView.scala
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
package gui
package impl

import javax.swing.Icon

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Workspace}

import scala.swing.{Component, Label}

// -------- Code --------

object CodeObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Code.Obj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Code)
  val prefix        : String    = "Code"
  def humanName     : String    = "Source Code"
  def tpe           : Obj.Type  = Code.Obj
  def category      : String    = ObjView.categMisc
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Code.Obj[S])(implicit tx: S#Tx): CodeObjView[S] with ListObjView[S] = {
    val value   = obj.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  type Config[S <: stm.Sys[S]] = ObjViewImpl.PrimitiveConfig[Code]

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = {
    val ggValue = new ComboBox(Seq(Code.FileTransform.name, Code.SynthGraph.name))
    val res = ObjViewImpl.primitiveConfig[S, Code](window, tpe = prefix, ggValue = ggValue, prepare =
      ggValue.selection.index match {
        case 0 => Some(Code.FileTransform(
          """|val aIn   = AudioFile.openRead(in)
            |val aOut  = AudioFile.openWrite(out, aIn.spec)
            |val bufSz = 8192
            |val buf   = aIn.buffer(bufSz)
            |var rem   = aIn.numFrames
            |while (rem > 0) {
            |  val chunk = math.min(bufSz, rem).toInt
            |  aIn .read (buf, 0, chunk)
            |  // ...
            |  aOut.write(buf, 0, chunk)
            |  rem -= chunk
            |  // checkAbort()
            |}
            |aOut.close()
            |aIn .close()
            |""".stripMargin))

        case 1 => Some(Code.SynthGraph(
          """|val in   = ScanIn("in")
            |val sig  = in
            |ScanOut("out", sig)
            |""".stripMargin
        ))

        case _  => None
      }
    )
    res.foreach(ok)
  }

  def makeObj[S <: Sys[S]](config: (String, Code))(implicit tx: S#Tx): List[Obj[S]] = {
    val (name, value) = config
    val peer  = Code.Obj.newVar[S](Code.Obj.newConst(value))
    val obj   = peer // Obj(Code.Elem(peer))
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Code.Obj[S]], var value: Code)
    extends CodeObjView[S]
    with ListObjView /* .Code */[S]
    with ObjViewImpl.Impl[S]
    with ListObjViewImpl.NonEditable[S] {

    type E[~ <: stm.Sys[~]] = Code.Obj[~]

    override def obj(implicit tx: S#Tx): Code.Obj[S] = objH()

    def factory: ObjView.Factory = CodeObjView

    // def isUpdateVisible(update: Any)(implicit tx: S#Tx): Boolean = false

    def isViewable = true

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = CodeFrame(obj, bottom = Nil)
      Some(frame)
    }

    def configureRenderer(label: Label): Component = {
      label.text = value.contextName
      label
    }
  }
}
trait CodeObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Code.Obj[S]
}