/*
 *  MarkdownObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
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
import de.sciss.lucre.expr.Type
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Cursor, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Markdown, Workspace}

object MarkdownObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Markdown[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Markdown)
  val prefix        : String    = "Markdown"
  def humanName     : String    = s"$prefix Text"
  def tpe           : Obj.Type  = Markdown
  def category      : String    = ObjView.categOrganisation
  def hasMakeDialog : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Markdown[S])(implicit tx: S#Tx): MarkdownObjView[S] with ListObjView[S] = {
    val ex    = obj
    val value = ex.value
    new Impl(tx.newHandle(obj), value).initAttrs(obj)
  }

  type Config[S <: stm.Sys[S]] = String

  def initMakeDialog[S <: Sys[S]](workspace: Workspace[S], window: Option[desktop.Window])
                                 (ok: Config[S] => Unit)
                                 (implicit cursor: stm.Cursor[S]): Unit = {
    val pane    = desktop.OptionPane.textInput(message = "Name", initial = prefix)
    pane.title  = s"New $humanName"
    val res = pane.show(window)
    res.foreach(ok(_))
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    val name  = config
    val value =
      """# Title
        |
        |body
        |""".stripMargin
    val obj   = Markdown.newVar(Markdown.newConst[S](value))
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

    val exprType: Type.Expr[Markdown.Value, Markdown] = Markdown

    def expr(implicit tx: S#Tx): Markdown[S] = obj

    def isEditable: Boolean = false // never within the list view

    def isViewable: Boolean = true

    def convertEditValue(v: Any): Option[String] = None

    override def openView(parent: Option[Window[S]])(implicit tx: S#Tx, workspace: Workspace[S],
                                                     cursor: Cursor[S]): Option[Window[S]] = {
      val frame = MarkdownEditorFrame(obj)
      Some(frame)
    }
  }
}
trait MarkdownObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): Markdown[S]
}