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

import java.util.Locale

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.{CodeFrame, ObjListView, ObjView}
import de.sciss.swingplus.ComboBox
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.Icon
import org.rogach.scallop

import scala.swing.{Component, Label}
import scala.util.Try

object CodeObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = Code.Obj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Code)
  val prefix        : String    = "Code"
  def humanName     : String    = "Source Code"
  def tpe           : Obj.Type  = Code.Obj
  def category      : String    = ObjView.categMisc
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Code.Obj[S])(implicit tx: S#Tx): CodeObjView[S] with ObjListView[S] = {
    val value   = obj.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, value: Code, const: Boolean = false)

  private def defaultCode(tpe: Code.Type): Code =
    Code(tpe.id, tpe.defaultSource)

  // cf. SP #58
  private lazy val codeSeq    = Code.types
  private lazy val codeNames  = codeSeq.map(_.humanName)
  private lazy val codeMap  : Map[String, Code.Type]  =
    codeSeq.iterator.map { tpe =>
      val nm = tpe.prefix.toLowerCase
      nm -> tpe
    } .toMap

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val ggValue = new ComboBox(codeNames)
    val res0 = ObjViewImpl.primitiveConfig[S, Code](window, tpe = prefix, ggValue = ggValue, prepare =
      Try(defaultCode(Code.getType(ggValue.selection.index)))
    )
    val res = res0.map(c => Config[S](name = c.name, value = c.value))
    done(res)
  }

  private implicit val ReadCode: scallop.ValueConverter[Code] = scallop.singleArgConverter { s =>
    val tpe = codeMap(s.toLowerCase(Locale.US))
    defaultCode(tpe)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    object p extends ObjViewCmdLineParser[Config[S]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[Code]    = trailArg("type", descr = codeMap.keysIterator.mkString("Code type (", ",", ")"))
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
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
    with ObjListView /* .Code */[S]
    with ObjViewImpl.Impl[S]
    with ObjListViewImpl.NonEditable[S] {

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

    def configureListCellRenderer(label: Label): Component = {
      label.text = value.tpe.humanName
      label
    }
  }
}
trait CodeObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Code.Obj[S]
}