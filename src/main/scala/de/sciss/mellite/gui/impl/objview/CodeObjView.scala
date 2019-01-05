/*
 *  CodeObjView.scala
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
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{CodeFrame, ListObjView, MessageException, ObjView}
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.Icon

import scala.swing.{Component, Label}
import scala.util.{Failure, Success}

// -------- Code --------

object CodeObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Code.Obj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Code)
  val prefix        : String    = "Code"
  def humanName     : String    = "Source Code"
  def tpe           : Obj.Type  = Code.Obj
  def category      : String    = ObjView.categMisc
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Code.Obj[S])(implicit tx: S#Tx): CodeObjView[S] with ListObjView[S] = {
    val value   = obj.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Code, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue = new ComboBox(Seq(Code.FileTransform.name, Code.SynthGraph.name))
    val res0 = ObjViewImpl.primitiveConfig[S, Code](window, tpe = prefix, ggValue = ggValue, prepare =
      ggValue.selection.index match {
        case 0 => Success(Code.FileTransform(
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

        case 1 => Success(Code.SynthGraph(
          """|val in   = ScanIn("in")
            |val sig  = in
            |ScanOut("out", sig)
            |""".stripMargin
        ))

        case _  =>
          Failure(MessageException("No code type selected"))
      }
    )
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = Code.Obj.newConst[S](value)
    val obj   = if (const) obj0 else Code.Obj.newVar[S](obj0)
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
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
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