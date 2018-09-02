/*
 *  ExprHistoryView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import de.sciss.lucre.confluent.Cursor
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Identifiable, Sys}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{defer, deferTx}
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.serial.Serializer
import de.sciss.swingplus.{ListView, SpinningProgressBar}
import de.sciss.synth.proc.{Confluent, Workspace}

import scala.language.higherKinds
import scala.swing.{BoxPanel, Component, Orientation, ScrollPane}

object ExprHistoryView {
  type S = Confluent
  type D = S#D

  var DEBUG = false

  def apply[A, Ex[~ <: Sys[~]] <: Expr[~, A]](workspace: Workspace.Confluent, expr: Ex[S])
              (implicit tx: S#Tx, serializer: Serializer[S#Tx, S#Acc, Ex[S]]): ViewHasWorkspace[S] = {
    val sys       = workspace.system
    val cursor    = Cursor[S, D](tx.inputAccess)(tx.durable, sys)
    val exprH: stm.Source[S#Tx, Expr[S, A]] = tx.newHandle(expr)  // IntelliJ highlight bug
    val pos0      = tx.inputAccess
    val time0     = pos0.info.timeStamp
    val val0      = expr.value

    val stop = expr match {
      case hid: Identifiable[_] =>
        val id    = hid.id // .asInstanceOf[confluent.Identifier[S]]
        val head  = id.path.head.toInt
        if (DEBUG) println(s"Id = $id, head = $head")
        head
      case _ => 0
    }

    val res = new Impl[A](workspace, cursor, exprH, pos0, time0, val0, stop = stop)
    deferTx(res.guiInit())
    res
  }

  private final class Impl[A](val workspace: Workspace[S],
                              val cursor: Cursor[S, D], exprH: stm.Source[S#Tx, Expr[S, A]],
                              pos0: S#Acc, time0: Long, value0: A, stop: Int)
    extends ViewHasWorkspace[S] with ComponentHolder[Component] {

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
      proc.onComplete(_ => defer(busy.spinning = false))
      proc.start()
      busy.spinning = true
      component   = new BoxPanel(Orientation.Vertical) {
        contents += scroll
        contents += busy
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      deferTx {
        proc.abort()
        busy.spinning = false
      }
      cursor.dispose()(tx.durable)    // XXX TODO - should we check the processor first?
    }
  }
}
