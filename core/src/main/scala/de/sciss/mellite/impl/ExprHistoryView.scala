/*
 *  ExprHistoryView.scala
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

package de.sciss.mellite.impl

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import de.sciss.lucre.confluent.Cursor
import de.sciss.lucre.swing.LucreSwing.{defer, deferTx}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.{Expr, Identified, Source, Txn, Workspace}
import de.sciss.mellite.UniverseView
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.serial.TFormat
import de.sciss.swingplus.{ListView, SpinningProgressBar}
import de.sciss.synth.proc
import de.sciss.synth.proc.{Confluent, Durable, Universe}

import scala.concurrent.ExecutionContext
import scala.swing.{BoxPanel, Component, Orientation, ScrollPane}

object ExprHistoryView {
  type S = Confluent
  type T = Confluent.Txn
  type D = Durable  .Txn

  var DEBUG = false

  def apply[A, Ex[~ <: Txn[~]] <: Expr[~, A]](workspace: proc.Workspace.Confluent, expr: Ex[T])
              (implicit tx: T, universe: Universe[T], format: TFormat[T, Ex[T]]): UniverseView[T] = {
//    val sys       = workspace.system
    // LUCRE4
    val cursor    = Cursor[T, D](tx.inputAccess)(tx.durable, ??? /*tx.system*/)
    val exprH: Source[T, Expr[T, A]] = tx.newHandle(expr)  // IntelliJ highlight bug
    val pos0      = tx.inputAccess
    val time0     = pos0.info.timeStamp
    val val0      = expr.value

    val stop = expr match {
      case hid: Identified[T] =>
        val id    = hid.id // .asInstanceOf[confluent.Identifier[T]]
        val head  = id.!.path.head.toInt
        if (DEBUG) println(s"Id = $id, head = $head")
        head
      case _ => 0
    }

    val res = new Impl[A](workspace, cursor, exprH, pos0, time0, val0, stop = stop)
    deferTx(res.guiInit())
    res
  }

  private final class Impl[A](val workspace: Workspace[T],
                              override val cursor: Cursor[T, D], exprH: Source[T, Expr[T, A]],
                              pos0: S#Acc, time0: Long, value0: A, stop: Int)(implicit val universe: Universe[T])
    extends UniverseView[T] with ComponentHolder[Component] {

    type C = Component

    private val mod     = ListView.Model.empty[String]
    private val format  = new SimpleDateFormat("yyyy MM dd MM | HH:mm:ss", Locale.US) // don't bother user with alpha characters

    private var busy: SpinningProgressBar = _

    private def mkString(time: Long, value: A): String = {
      val date  = format.format(new Date(time))
      s"$date    $value"
    }

    private object proc extends Processor[Unit] with ProcessorImpl[Unit, Processor[Unit]] {
      protected def body(): Unit = {
        var path    = pos0
        var ok      = true
        var time    = time0
        var value   = value0

        def addEntry(): Unit = {
          val s = mkString(time, value)
          if (DEBUG) println(s"----add $path, $s")
          defer {
            mod.prepend(s)
          }
        }

        while (ok) {
          val (newTime, newValue, newPath) = cursor.stepFrom(path) { implicit tx =>
            if (DEBUG) println(s"----path = $path")
            val v     = try {
              val expr  = exprH()
              if (DEBUG) println(s"----try $expr")
              val res   = expr.value
              if (DEBUG) println(s"----ok $res")
              res
            } catch {
              case _: NoSuchElementException => value // XXX TODO - ugly ugly ugly
            }
            val time  = path.info.timeStamp
            val path1 = path.takeUntil(time - 1L)
            (time, v, path1)
          }
          checkAborted()

          if (value != newValue) {
            addEntry()
            value     = newValue
            time      = newTime
          }

          ok    = path != newPath && (newPath.last.toInt >= stop)
          path  = newPath
        }
        addEntry()
      }
    }

    def guiInit(): Unit = {
      val lv      = new ListView(mod)
      lv.prototypeCellValue = s"${mkString(System.currentTimeMillis(), value0)}XXXXX"
      val scroll  = new ScrollPane(lv)
      scroll.peer.putClientProperty("styleId", "undecorated")
      scroll.border = null
      busy        = new SpinningProgressBar
      import ExecutionContext.Implicits.global
      proc.onComplete(_ => defer(busy.spinning = false))
      proc.start()
      busy.spinning = true
      component   = new BoxPanel(Orientation.Vertical) {
        contents += scroll
        contents += busy
      }
    }

    def dispose()(implicit tx: T): Unit = {
      deferTx {
        proc.abort()
        busy.spinning = false
      }
      cursor.dispose()(tx.durable)    // XXX TODO - should we check the processor first?
    }
  }
}
