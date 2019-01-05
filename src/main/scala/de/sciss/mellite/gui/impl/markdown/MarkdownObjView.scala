/*
 *  MarkdownObjView.scala
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

package de.sciss.mellite
package gui.impl.markdown

import javax.swing.Icon
import de.sciss.desktop
import de.sciss.lucre.expr.Type
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, ObjViewImpl}
import de.sciss.mellite.gui.{GUI, ListObjView, MarkdownEditorFrame, ObjView, Shapes}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Markdown, Universe}

object MarkdownObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Markdown[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Markdown)
  val prefix        : String    = "Markdown"
  def humanName     : String    = s"$prefix Text"
  def tpe           : Obj.Type  = Markdown
  def category      : String    = ObjView.categOrganisation
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Markdown[S])(implicit tx: S#Tx): MarkdownObjView[S] with ListObjView[S] = {
    val ex    = obj
    val value = ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, contents: Option[String] = None, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
                                 (implicit universe: Universe[S]): Unit = {
    val pane    = desktop.OptionPane.textInput(message = "Name", initial = prefix)
    pane.title  = s"New $humanName"
    val res0    = GUI.optionToAborted(pane.show(window))
    val res     = res0.map(name => Config[S](name = name))
    done(res)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String]): MakeResult[S] = {
    val default: Config[S] = Config()
    val p = ObjViewCmdLineParser[S](this)
    import p._
    opt[String]('n', "name")
      .text(s"Object's name (default: $prefix)")
      .action((v, c) => c.copy(name = v))

    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[String]("contents")
      .text(s"Markdown text")
      .action((v, c) => c.copy(contents = Some(v)))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val value = contents.getOrElse(
      """# Title
        |
        |body
        |""".stripMargin
    )
    val obj0  = Markdown.newConst[S](value)
    val obj   = if (const) obj0 else Markdown.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO make private
  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Markdown[S]], var value: String)
    extends MarkdownObjView[S]
      with ListObjView[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, Markdown.Value, Markdown]
      with ListObjViewImpl.StringRenderer {

    override def obj(implicit tx: S#Tx): Markdown[S] = objH()

    type E[~ <: stm.Sys[~]] = Markdown[~]

    def factory: ObjView.Factory = MarkdownObjView

    def exprType: Type.Expr[Markdown.Value, Markdown] = Markdown

    def expr(implicit tx: S#Tx): Markdown[S] = obj

    def isEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    def convertEditValue(v: Any): Option[String] = None

    override def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val frame = MarkdownEditorFrame(obj)
      Some(frame)
    }
  }
}
trait MarkdownObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Markdown[S]
}