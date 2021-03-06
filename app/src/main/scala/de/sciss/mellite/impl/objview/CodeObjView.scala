/*
 *  CodeObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.objview

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.{CodeFrame, ObjListView, ObjView}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Code, Universe}
import de.sciss.swingplus.ComboBox
import org.rogach.scallop

import java.util.Locale
import javax.swing.Icon
import scala.swing.{Component, Label}
import scala.util.Try

object CodeObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = Code.Obj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Code)
  val prefix        : String    = "Code"
  def humanName     : String    = "Source Code"
  def tpe           : Obj.Type  = Code.Obj
  def category      : String    = ObjView.categMisc
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: Code.Obj[T])(implicit tx: T): CodeObjView[T] with ObjListView[T] = {
    val value   = obj.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: Code, const: Boolean = false)

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

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val ggValue = new ComboBox(codeNames)
    val res0 = ObjViewImpl.primitiveConfig[T, Code](window, tpe = prefix, ggValue = ggValue, prepare =
      Try {
        val tpe = codeSeq(ggValue.selection.index)
        defaultCode(tpe)
      }
    )
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  private implicit val ReadCode: scallop.ValueConverter[Code] = scallop.singleArgConverter { s =>
    val tpe = codeMap(s.toLowerCase(Locale.US))
    defaultCode(tpe)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[Code]    = trailArg("type", descr = codeMap.keysIterator.mkString("Code type (", ",", ")"))
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = Code.Obj.newConst[T](value)
    val obj   = if (const) obj0 else Code.Obj.newVar[T](obj0)
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, Code.Obj[T]], var value: Code)
    extends CodeObjView[T]
    with ObjListView /* .Code */[T]
    with ObjViewImpl.Impl[T]
    with ObjListViewImpl.NonEditable[T] {

    override def obj(implicit tx: T): Code.Obj[T] = objH()

    def factory: ObjView.Factory = CodeObjView

    // def isUpdateVisible(update: Any)(implicit tx: T): Boolean = false

    def isViewable = true

    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
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
trait CodeObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Code.Obj[T]
}