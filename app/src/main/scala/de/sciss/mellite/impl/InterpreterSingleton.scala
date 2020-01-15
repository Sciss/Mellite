/*
 *  InterpreterSingleton.scala
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

import de.sciss.mellite.Mellite.executionContext
import de.sciss.scalainterpreter.Interpreter

import scala.collection.immutable.{IndexedSeq => Vec}

object InterpreterSingleton {
  private val sync = new AnyRef

  private var functions = Vec   .empty[Interpreter => Unit]
  private var inOpt     = Option.empty[Interpreter]

  def apply(fun: Interpreter => Unit): Unit =
    sync.synchronized {
      inOpt match {
        case Some(in) =>
          fun(in)
        case _ =>
          makeOne
          functions :+= fun
      }
    }

  private lazy val makeOne: Unit = {
    val cfg = Interpreter.Config()
    cfg.imports ++= Seq(
      "de.sciss.synth",
      "synth._",
      "ugen._"
    )
    Interpreter.async(cfg).foreach { in =>
      sync.synchronized {
        inOpt = Some(in)
        val f = functions
        functions  = Vector.empty
        f.foreach(_.apply(in))
      }
    }
  }
}
