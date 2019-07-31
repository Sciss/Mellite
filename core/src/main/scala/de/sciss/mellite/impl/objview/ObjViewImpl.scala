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

package de.sciss.mellite.impl.objview

import java.awt.geom.Path2D
import java.awt.{Color => AWTColor}

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.confluent.Access
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.expr.{CellView, Expr, StringObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Folder, Obj}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.edit.EditFolderInsertObj
import de.sciss.mellite.impl.{ExprHistoryView, WindowImpl}
import de.sciss.mellite.{GUI, ObjView, UniverseView}
import de.sciss.numbers.Implicits._
import de.sciss.processor.Processor.Aborted
import de.sciss.serial.Serializer
import de.sciss.synth.proc.{Color, Confluent, ObjKeys, TimeRef, Universe, Workspace}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit
import org.rogach.scallop

import scala.language.higherKinds
import scala.swing.Component
import scala.util.{Failure, Try}

object ObjViewImpl {
  object TimeArg {
    // XXX TODO --- support '[HH:]MM:SS[.mmm]'
    implicit val Read: scallop.ValueConverter[TimeArg] = scallop.singleArgConverter[TimeArg] { s =>
      val t = s.trim
      val res = if (t.endsWith("s")) {
        val n = t.substring(0, t.length - 1).toDouble
        Sec(n)
      } else {
        val n = t.toLong
        Frames(n)
      }
      res
    }

//    implicit object Read extends scopt.Read[TimeArg] {
//      def arity: Int = 1
//
//      def reads: String => TimeArg = { s =>
//        val t = s.trim
//        if (t.endsWith("s")) {
//          val n = t.substring(0, t.length - 1).toDouble
//          Sec(n)
//        } else {
//          val n = t.toLong
//          Frames(n)
//        }
//      }
//    }

    final case class Sec(n: Double) extends TimeArg {
      def frames(sr: Double): Long = (n * TimeRef.SampleRate + 0.5).toLong
    }

    final case class Frames(n: Long) extends TimeArg {
      override def toString: String = n.toString

      def frames(sr: Double): Long = n
    }
  }
  sealed trait TimeArg {
    def frames(sr: Double = TimeRef.SampleRate): Long
  }

  object GainArg {
    implicit val Read: scallop.ValueConverter[GainArg] = scallop.singleArgConverter { s =>
      val t = s.trim
      val frames = if (t.endsWith("dB")) {
        val db = t.substring(0, t.length - 2).toDouble
        db.dbAmp
      } else {
        t.toDouble
      }
      GainArg(frames)
    }

//    implicit object Read extends scopt.Read[GainArg] {
//      def arity: Int = 1
//
//      def reads: String => GainArg = { s =>
//        val t = s.trim
//        val frames = if (t.endsWith("dB")) {
//          val db = t.substring(0, t.length - 2).toDouble
//          import numbers.Implicits._
//          db.dbAmp
//        } else {
//          t.toDouble
//        }
//        GainArg(frames)
//      }
//    }
  }
  final case class GainArg(linear: Double)

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
    val fill = if (GUI.isDarkSkin) colrIconDark else AWTColor.black
    raphael.Icon(extent = IconExtent, fill = fill)(shape)
  }

  trait Impl[S <: stm.Sys[S]] extends ObjView[S] /* with ModelImpl[ObjView.Update[S]] */
    with ObservableImpl[S, ObjView.Update[S]] {

    override def toString = s"ElementView.${factory.prefix}(name = $name)"

    def obj(implicit tx: S#Tx): Repr = objH()

    /** Forwards to factory. */
    def humanName: String = factory.humanName

    /** Forwards to factory. */
    def icon: Icon = factory.icon

    var nameOption : Option[String] = None
    var colorOption: Option[Color ] = None

    private[this] var disposables = List.empty[Disposable[S#Tx]]

    final protected def addDisposable(d: Disposable[S#Tx]): Unit =
      disposables ::= d

//    final def addDisposables(d: List[Disposable[S#Tx]])(implicit tx: S#Tx): Unit =
//      disposables :::= d

    def dispose()(implicit tx: S#Tx): Unit = disposables.foreach(_.dispose())

    final protected def deferAndRepaint(body: => Unit)(implicit tx: S#Tx): Unit = {
      deferTx(body)
      fire(ObjView.Repaint(this))
    }

    /** Sets name and color. */
    def initAttrs(obj: Obj[S])(implicit tx: S#Tx): this.type = {
      val attr      = obj.attr

      val nameView  = CellView.attr[S, String, StringObj](attr, ObjKeys.attrName)
      addDisposable(nameView.react { implicit tx =>opt =>
        deferAndRepaint {
          nameOption = opt
        }
      })
      nameOption   = nameView()

      val colorView = CellView.attr[S, Color, Color.Obj](attr, ObjView.attrColor)
      addDisposable(colorView.react { implicit tx =>opt =>
        deferAndRepaint {
          colorOption = opt
        }
      })
      colorOption  = colorView()
      this
    }
  }

  trait SimpleExpr[S <: Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ExprLike[S, A, Ex]
    with ObjViewImpl.Impl[S] {

    def value: A

    protected def value_=(x: A): Unit

    protected def exprValue: A = value
    protected def exprValue_=(x: A): Unit = value = x

    def init(ex: Ex[S])(implicit tx: S#Tx): this.type = {
      initAttrs(ex)
      addDisposable(ex.changed.react { implicit tx =>upd =>
        deferTx {
          exprValue = upd.now
        }
        fire(ObjView.Repaint(this))
      })
      this
    }
  }

  trait ExprLike[S <: stm.Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ObjView[S] {
//    type Repr = Ex[S]

    protected var exprValue: A

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
          implicit val ser: Serializer[Confluent#Tx, Access[Confluent], Ex[Confluent]] = exprType.serializer[Confluent]
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
