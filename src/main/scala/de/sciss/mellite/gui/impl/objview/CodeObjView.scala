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
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.{CodeFrame, ListObjView, MessageException, ObjView}
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.Icon

import scala.swing.{Component, Label}
import scala.util.{Failure, Success, Try}

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

  private def defaultCode(id: Int): Try[Code] = id match {
    case Code.SynthGraph.id => Success(Code.SynthGraph(
      """|val in   = ScanIn("in")
         |val sig  = in
         |ScanOut("out", sig)
         |""".stripMargin))

    case Code.Action.id => Success(Code.Action(
      """|println("bang!")
         |""".stripMargin))

    case _ =>
      Failure(MessageException("No code type selected"))
  }

  // cf. SP #58
  private lazy val codeSeq  : Seq[Code.Type]          = Seq(Code.SynthGraph     , Code.Action     )
  private lazy val codeNames: Seq[String]             = Seq(Code.SynthGraph.name, Code.Action.name)
  private lazy val codeMap  : Map[String, Code.Type]  =
    codeNames.iterator.zip(codeSeq.iterator).map { case (n, tpe) =>
      val nm = n.filterNot(_.isSpaceChar).toLowerCase
      nm -> tpe
    } .toMap

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue = new ComboBox(codeNames)
    val res0 = ObjViewImpl.primitiveConfig[S, Code](window, tpe = prefix, ggValue = ggValue, prepare =
      defaultCode(ggValue.selection.index)
    )
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  private implicit object ReadCode extends scopt.Read[Code] {
    def arity: Int = 1

    def reads: String => Code = { s =>
      val tpe = codeMap(s.toLowerCase)
      defaultCode(tpe.id).get
    }
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = Config(value = null)
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant offset instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[Code]("type")
      .text(codeMap.keysIterator.mkString("Code type (", ",", ")"))
      .action((v, c) => c.copy(value = v))

    parseConfig(args, default)
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