/*
 *  ObjViewImpl.scala
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

import java.awt.geom.Path2D
import java.awt.{Color => AWTColor}

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.confluent.Access
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.expr.{CellView, Expr, StringObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Folder, Obj}
import de.sciss.lucre.swing.{Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditFolderInsertObj
import de.sciss.mellite.gui.impl.{ExprHistoryView, WindowImpl}
import de.sciss.mellite.gui.{GUI, ObjView}
import de.sciss.mellite.{Cf, Mellite}
import de.sciss.processor.Processor.Aborted
import de.sciss.serial.Serializer
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Color, Confluent, ObjKeys, Universe, Workspace}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.language.higherKinds
import scala.swing.Component
import scala.util.{Failure, Try}

object ObjViewImpl {

  def nameOption[S <: stm.Sys[S]](obj: Obj[S])(implicit tx: S#Tx): Option[String] =
    obj.attr.$[StringObj](ObjKeys.attrName).map(_.value)

  // -----------------------------

  def addObject[S <: Sys[S]](name: String, parent: Folder[S], obj: Obj[S])
                            (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    // val parent = targetFolder
    // parent.addLast(obj)
    val idx = parent.size
//    implicit val folderSer = Folder.serializer[S]
    EditFolderInsertObj[S](name, parent, idx, obj)
  }

  final case class PrimitiveConfig[A](name: String, value: A)

  /** Displays a simple new-object configuration dialog, prompting for a name and a value. */
  def primitiveConfig[S <: Sys[S], A](window: Option[desktop.Window], tpe: String, ggValue: Component,
                                      prepare: => Try[A]): Try[PrimitiveConfig[A]] = {
    val nameOpt = GUI.keyValueDialog(value = ggValue, title = s"New $tpe", defaultName = tpe, window = window)
    nameOpt match {
      case Some(name) =>
        prepare.map { value =>
          PrimitiveConfig(name, value)
        }

      case None => Failure(Aborted())
    }
  }

  private[this] val colrIconDark = new AWTColor(200, 200, 200)

  final val IconExtent = 16

  def raphaelIcon(shape: Path2D => Unit): Icon = {
    val fill = if (Mellite.isDarkSkin) colrIconDark else AWTColor.black
    raphael.Icon(extent = IconExtent, fill = fill)(shape)
  }

  trait Impl[S <: stm.Sys[S]] extends ObjView[S] /* with ModelImpl[ObjView.Update[S]] */
    with ObservableImpl[S, ObjView.Update[S]] {

    override def toString = s"ElementView.${factory.prefix}(name = $name)"

    def objH: stm.Source[S#Tx, Obj[S]]

    def obj(implicit tx: S#Tx): Obj[S] = objH()

    /** Forwards to factory. */
    def humanName: String = factory.humanName

    /** Forwards to factory. */
    def icon: Icon = factory.icon

    var nameOption : Option[String] = None
    var colorOption: Option[Color] = None

    protected var disposables: List[Disposable[S#Tx]] = Nil

    def dispose()(implicit tx: S#Tx): Unit = disposables.foreach(_.dispose())

    final protected def deferAndRepaint(body: => Unit)(implicit tx: S#Tx): Unit = {
      deferTx(body)
      fire(ObjView.Repaint(this))
    }

    /** Sets name and color. */
    def initAttrs(obj: Obj[S])(implicit tx: S#Tx): this.type = {
      val attr      = obj.attr

      val nameView  = CellView.attr[S, String, StringObj](attr, ObjKeys.attrName)
      disposables ::= nameView.react { implicit tx => opt =>
        deferAndRepaint {
          nameOption = opt
        }
      }
      nameOption   = nameView()

      val colorView = CellView.attr[S, Color, Color.Obj](attr, ObjView.attrColor)
      disposables ::= colorView.react { implicit tx => opt =>
        deferAndRepaint {
          colorOption = opt
        }
      }
      colorOption  = colorView()
      this
    }
  }

  trait ExprLike[S <: stm.Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ObjView[S] {
    protected def expr(implicit tx: S#Tx): Ex[S]

    protected implicit def exprType: Type.Expr[A, Ex]

    // XXX TODO - this is a quick hack for demo
    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      universe.workspace match {
        case cf: Workspace.Confluent =>
          // XXX TODO - all this casting is horrible
          implicit val uni: Universe[Confluent] = universe.asInstanceOf[Universe[Confluent]]
          implicit val ctx: Confluent#Tx = tx.asInstanceOf[Confluent#Tx]
          implicit val ser: Serializer[Confluent#Tx, Access[Cf], Ex[Cf]] = exprType.serializer[Confluent]
          val name = CellView.name[Confluent](obj.asInstanceOf[Obj[Confluent]])
            .map(n => s"History for '$n'")
          val w: Window[Confluent] = new WindowImpl[Confluent](name) {
            val view: UniverseView[Confluent] = ExprHistoryView[A, Ex](cf, expr.asInstanceOf[Ex[Confluent]])
            init()
          }
          Some(w.asInstanceOf[Window[S]])
        case _ => None
      }
    }
  }

  /** A trait that when mixed in provides `isViewable` and `openView` as non-op methods. */
  trait NonViewable[S <: stm.Sys[S]] extends ObjView[S] {

    def isViewable: Boolean = false

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = None
  }
}
