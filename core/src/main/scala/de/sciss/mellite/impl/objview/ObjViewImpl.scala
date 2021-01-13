/*
 *  ObjViewImpl.scala
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

import java.awt.geom.Path2D
import java.awt.{Color => AWTColor}

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.impl.ObservableImpl
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Disposable, Expr, Folder, Obj, StringObj, Txn => LTxn}
import de.sciss.mellite.edit.EditFolderInsertObj
import de.sciss.mellite.impl.{ExprHistoryView, WindowImpl}
import de.sciss.mellite.{GUI, ObjView, UniverseView}
import de.sciss.numbers.Implicits._
import de.sciss.processor.Processor.Aborted
import de.sciss.serial.TFormat
import de.sciss.proc.{Color, Confluent, ObjKeys, TimeRef, Universe, Workspace}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit
import org.rogach.scallop

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

  def nameOption[T <: LTxn[T]](obj: Obj[T])(implicit tx: T): Option[String] =
    obj.attr.$[StringObj](ObjKeys.attrName).map(_.value)

  // -----------------------------

  def addObject[T <: Txn[T]](name: String, parent: Folder[T], obj: Obj[T])
                            (implicit tx: T, cursor: Cursor[T]): UndoableEdit = {
    // val parent = targetFolder
    // parent.addLast(obj)
    val idx = parent.size
//    implicit val folderSer = Folder.serializer[T]
    EditFolderInsertObj[T](name, parent, idx, obj)
  }

  final case class PrimitiveConfig[A](name: String, value: A)

  /** Displays a simple new-object configuration dialog, prompting for a name and a value. */
  def primitiveConfig[T <: Txn[T], A](window: Option[desktop.Window], tpe: String, ggValue: Component,
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

  trait Impl[T <: LTxn[T]] extends ObjView[T] /* with ModelImpl[ObjView.Update[T]] */
    with ObservableImpl[T, ObjView.Update[T]] {

    override def toString = s"ElementView.${factory.prefix}(name = $name)"

    def obj(implicit tx: T): Repr = objH()

    /** Forwards to factory. */
    def humanName: String = factory.humanName

    /** Forwards to factory. */
    def icon: Icon = factory.icon

    var nameOption : Option[String] = None
    var colorOption: Option[Color ] = None

    private[this] var disposables = List.empty[Disposable[T]]

    final protected def addDisposable(d: Disposable[T]): Unit =
      disposables ::= d

//    final def addDisposables(d: List[Disposable[T]])(implicit tx: T): Unit =
//      disposables :::= d

    def dispose()(implicit tx: T): Unit = disposables.foreach(_.dispose())

    final protected def deferAndRepaint(body: => Unit)(implicit tx: T): Unit = {
      deferTx(body)
      fire(ObjView.Repaint(this))
    }

    /** Sets name and color. */
    def initAttrs(obj: Obj[T])(implicit tx: T): this.type = {
      val attr      = obj.attr

      val nameView  = CellView.attr[T, String, StringObj](attr, ObjKeys.attrName)
      addDisposable(nameView.react { implicit tx =>opt =>
        deferAndRepaint {
          nameOption = opt
        }
      })
      nameOption   = nameView()

      val colorView = CellView.attr[T, Color, Color.Obj](attr, ObjView.attrColor)
      addDisposable(colorView.react { implicit tx =>opt =>
        deferAndRepaint {
          colorOption = opt
        }
      })
      colorOption  = colorView()
      this
    }
  }

  trait SimpleExpr[T <: Txn[T], A, Ex[~ <: LTxn[~]] <: Expr[~, A]] extends ExprLike[T, A, Ex]
    with ObjViewImpl.Impl[T] {

    def value: A

    protected def value_=(x: A): Unit

    protected def exprValue: A = value
    protected def exprValue_=(x: A): Unit = value = x

    def init(ex: Ex[T])(implicit tx: T): this.type = {
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

  trait ExprLike[T <: LTxn[T], A, Ex[~ <: LTxn[~]] <: Expr[~, A]] extends ObjView[T] {
//    type Repr = Ex[T]

    protected var exprValue: A

    protected def expr(implicit tx: T): Ex[T]

    protected implicit def exprType: Expr.Type[A, Ex]

    // XXX TODO - this is a quick hack for demo
    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      universe.workspace match {
        case cf: Workspace.Confluent =>
          // XXX TODO - all this casting is horrible
          type CfT = Confluent.Txn
          implicit val uni: Universe[CfT] = universe.asInstanceOf[Universe[CfT]]
          implicit val ctx: CfT           = tx.asInstanceOf[CfT]
          implicit val fmt: TFormat[CfT, Ex[CfT]] = exprType.format[CfT]
          val name = CellView.name[CfT](obj.asInstanceOf[Obj[CfT]])
            .map(n => s"History for '$n'")
          val w: Window[CfT] = new WindowImpl[CfT](name) {
            val view: UniverseView[CfT] = ExprHistoryView[A, Ex](cf, expr.asInstanceOf[Ex[CfT]])
            init()
          }
          Some(w.asInstanceOf[Window[T]])
        case _ => None
      }
    }
  }

  /** A trait that when mixed in provides `isViewable` and `openView` as non-op methods. */
  trait NonViewable[T <: LTxn[T]] extends ObjView[T] {

    def isViewable: Boolean = false

    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = None
  }
}
