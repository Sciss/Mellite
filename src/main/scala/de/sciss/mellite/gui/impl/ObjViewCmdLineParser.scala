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

import de.sciss.kollflitz.{ISeq, Vec}
import de.sciss.lucre.stm.Sys
import de.sciss.mellite.gui.{MessageException, ObjView}
import de.sciss.processor.Processor.Aborted
import org.rogach.scallop.exceptions.{Help, ScallopException, ScallopResult, Version}
import org.rogach.scallop.{ScallopConf, ScallopOption, ValueConverter, throwError}

import scala.util.{Failure, Success, Try}

object ObjViewCmdLineParser {
  def apply[S <: Sys[S]](f: ObjView.Factory, args: ISeq[String]): ObjViewCmdLineParser[f.Config[S]] =
    new ObjViewCmdLineParser[f.Config[S]](f, args)
}
class ObjViewCmdLineParser[C](private val f: ObjView.Factory, args: ISeq[String])
  extends ScallopConf(args) {

  printedName = f.prefix
//  version(printedName)
  banner(printedName)
  footer("")

  private[this] var _error    = Option.empty[String]
  private[this] var _aborted  = false

  override protected def onError(e: Throwable): Unit = e match {
    case r: ScallopResult if !throwError.value => r match {
      case Help("") =>
        builder.printHelp()
        _aborted = true
      case Help(subName) =>
        builder.findSubbuilder(subName).get.printHelp()
        _aborted = true
      case Version =>
        builder.vers.foreach(println)
        _aborted = true
      case ScallopException(message) =>
        _error = Some(message)
    }
    case _ => throw e
  }

    //  override def terminate(exitState: Either[String, Unit]): Unit =
//    if (exitState.isRight) _aborted = true
//
//  override def reportError(msg: String): Unit = _error = msg

  type Opt[A] = ScallopOption[A]

  val name: Opt[String] = opt(descr = s"Object's name (default: ${f.prefix})", default = Some(f.prefix))

  def nameOption: Option[String] = {
    val n = name()
    if (n.isEmpty) None else Some(n)
  }

  private[this] val collectBool: PartialFunction[String, Boolean] = {
    case "0" | "F" | "false"  => false
    case "1" | "T" | "true"   => true
  }

  def boolArg(name    : String  = null,
              descr   : String  = "",
              required: Boolean = true
             ): ScallopOption[Boolean] =
    trailArg[String](name = name, descr = descr, required = required,
      validate = collectBool.isDefinedAt
    ).collect(collectBool)

  def boolOpt(name    : String  = null,
              descr   : String  = "",
              required: Boolean = true,
              default : Option[Boolean] = Some(false)
             ): ScallopOption[Boolean] =
    opt[String](name = name, descr = descr, required = required,
      validate = collectBool.isDefinedAt
    ).collect(collectBool).orElse(default)

  def nameOr(fallback: => Option[String]): String = {
    val n = name()
    if (n.isEmpty) fallback.getOrElse(n) else n
  }

  private def collectVec[A](s: String)(implicit peer: ValueConverter[A]): Option[Vec[A]] =
    s.split(',').filter(_.nonEmpty).foldLeft(Option(Vec.empty[A])) {
      case (Some(v0), w) =>
        peer.parse(("", w :: Nil) :: Nil) match {
          case Left(_)    => None
          case Right(opt) => opt.map(v0 :+ _)
        }
      case (None, _) => None
    }

  def vecArg[A](name    : String  = null,
                descr   : String  = "",
                required: Boolean = true)(implicit peer: ValueConverter[A]): ScallopOption[Vec[A]] =
    trailArg[String](name = name, descr = descr, required = required,
      validate = collectVec(_).isDefined
    ).map(collectVec[A](_).get) // XXX TODO d'oh this is ugly

  def parse(ok: => C): Try[C] = {
    verify()
    if (_aborted) Failure(Aborted()) else _error match {
      case Some(msg)  => Failure(MessageException(msg))
      case None       => Success(ok)
    }
  }

  // how annoying can it be...

//  private[this] var showedUsage = false
//
//  override def showUsage(): Unit = if (!showedUsage) {
//    super.showUsage()
//    showedUsage = true
//  }
//
//  override def showUsageAsError(): Unit = if (!showedUsage) {
//    super.showUsageAsError()
//    showedUsage = true
//  }
//
//  override def showUsageOnError: Boolean = true // even if we offer '--help'

  // constructor
//  help("help").text("Prints this usage text")

}