/*
 *  TagObjView.scala
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
import de.sciss.icons.raphael
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.ObjViewCmdLineParser
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Tag, Universe}
import de.sciss.processor.Processor.Aborted

import javax.swing.Icon
import scala.swing.{Component, Label}
import scala.util.{Failure, Success}

object TagObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = Tag[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Tag)
  val prefix        : String    = "Tag"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Tag
  def category      : String    = ObjView.categMisc
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: E[T])(implicit tx: T): TagObjView[T] with ObjListView[T] = {
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val pane = desktop.OptionPane.textInput("Name", initial = humanName)
    pane.title  = s"New $humanName"
    val res0  = pane.show(window)
    val res   = res0 match {
      case Some(n)  => Success(Config[T](name = n))
      case None     => Failure(Aborted())
    }
    done(res)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args)
    p.parse(Config(name = p.name()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj = Tag[T]()
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, Tag[T]])
    extends TagObjView                [T]
      with ObjListView                [T]
      with ObjViewImpl.Impl           [T]
      with ObjViewImpl.NonViewable    [T]
      with ObjListViewImpl.NonEditable[T] {

    def factory: ObjView.Factory = TagObjView

    def value: Any = ()

    def configureListCellRenderer(label: Label): Component = label
  }
}
trait TagObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Tag[T]
}