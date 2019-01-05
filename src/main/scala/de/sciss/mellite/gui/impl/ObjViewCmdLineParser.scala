/*
 *  ObjViewCmdLineParser.scala
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

package de.sciss.mellite.gui.impl

import de.sciss.lucre.stm.Sys
import de.sciss.mellite.gui.{MessageException, ObjView}
import de.sciss.processor.Processor.Aborted
import scopt.OptionParser

import scala.util.{Failure, Success, Try}

object ObjViewCmdLineParser {
  def apply[S <: Sys[S]](f: ObjView.Factory): ObjViewCmdLineParser[f.Config[S]] =
    new ObjViewCmdLineParser[f.Config[S]](f)
}
class ObjViewCmdLineParser[C](private val f: ObjView.Factory)
  extends OptionParser[C](f.prefix) {

  private[this] var _error    = ""
  private[this] var _aborted  = false

  override def terminate(exitState: Either[String, Unit]): Unit =
    if (exitState.isRight) _aborted = true

  override def reportError(msg: String): Unit = _error = msg

  def parseConfig(args: List[String], default: C): Try[C] = {
    val opt = parse(args, default)
    if (_aborted) Failure(Aborted()) else opt match {
      case Some(value)  => Success(value)
      case None         => Failure(MessageException(_error))
    }
  }

  override def showUsageOnError: Boolean = true // even if we offer '--help'

  // constructor
  help("help").text("Prints this usage text")
}