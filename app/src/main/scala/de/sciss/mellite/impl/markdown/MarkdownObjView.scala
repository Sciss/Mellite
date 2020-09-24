/*
 *  MarkdownObjView.scala
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

package de.sciss.mellite.impl.markdown

import de.sciss.desktop
import de.sciss.lucre.expr.Type
import de.sciss.lucre.{Obj, stm}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.{Sys, Txn}
import de.sciss.mellite.{GUI, MarkdownFrame, ObjListView, ObjView}
import de.sciss.mellite.Shapes
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.impl.objview.{ObjListViewImpl, ObjViewImpl}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Markdown, Universe}
import javax.swing.Icon

object MarkdownObjView extends ObjListView.Factory {
  type E[~ <: stm.Sys[~]] = Markdown[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Markdown)
  val prefix        : String    = "Markdown"
  def humanName     : String    = s"$prefix Text"
  def tpe           : Obj.Type  = Markdown
  def category      : String    = ObjView.categOrganization
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: Markdown[T])(implicit tx: T): MarkdownObjView[T] with ObjListView[T] = {
    val ex    = obj
    val value = ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final case class Config[S <: stm.Sys[T]](name: String = prefix, contents: Option[String] = None, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val pane    = desktop.OptionPane.textInput(message = "Name", initial = prefix)
    pane.title  = s"New $humanName"
    val res0    = GUI.optionToAborted(pane.show(window))
    val res     = res0.map(name => Config[T](name = name))
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const   : Opt[Boolean]  = opt(descr = s"Make constant instead of variable")
      val contents: Opt[String]   = trailArg(required = false, descr = "Markdown text")
    }
    p.parse(Config(name = p.name(), contents = p.contents.toOption, const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val value = contents.getOrElse(
      """# Title
        |
        |body
        |""".stripMargin
    )
    val obj0  = Markdown.newConst[T](value)
    val obj   = if (const) obj0 else Markdown.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO make private
  final class Impl[T <: Txn[T]](val objH: Source[T, Markdown[T]], var value: String)
    extends ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.SimpleExpr[T, Markdown.Value, Markdown]
      with ObjListViewImpl.StringRenderer
      with MarkdownObjView[T] {

    override def obj(implicit tx: T): Markdown[T] = objH()

    def factory: ObjView.Factory = MarkdownObjView

    def exprType: Type.Expr[Markdown.Value, Markdown] = Markdown

    def expr(implicit tx: T): Markdown[T] = obj

    def isListCellEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    def convertEditValue(v: Any): Option[String] = None

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val frame = MarkdownFrame.editor(obj)
      Some(frame)
    }
  }
}
trait MarkdownObjView[S <: stm.Sys[T]] extends ObjView[T] {
  override type Repr = Markdown[T]
}