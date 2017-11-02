/*
 *  InterpreterSingleton.scala
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
package impl

import de.sciss.scalainterpreter.Interpreter

import scala.collection.immutable.{IndexedSeq => Vec}

object InterpreterSingleton {
  private val sync = new AnyRef

  private var funs  = Vec   .empty[Interpreter => Unit]
  private var inOpt = Option.empty[Interpreter]

  //   object Result {
  //      var value : Any = ()
  //   }
  //
  //   def wrap( code: String ) : String = {
  //      "de.sciss.mellite.gui.impl.InterpreterSingleton.Result.value = {" + code + "}"
  //   }

  def apply(fun: Interpreter => Unit): Unit =
    sync.synchronized {
      inOpt match {
        case Some(in) =>
          fun(in)
        case _ =>
          makeOne
          funs :+= fun
      }
    }

  private lazy val makeOne: Unit = {
    val cfg = Interpreter.Config()
    cfg.imports ++= Seq(
      "de.sciss.synth",
      "synth._",
      "ugen._"
    )
    //      cfg.bindings += NamedParam( Result )
    Interpreter.async(cfg).foreach { in =>
      sync.synchronized {
        inOpt = Some(in)
        val f = funs
        funs  = Vector.empty
        f.foreach(_.apply(in))
      }
    }
  }
}
