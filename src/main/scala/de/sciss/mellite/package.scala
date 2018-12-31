/*
 *  package.scala
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

package de.sciss

import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import de.sciss.synth.proc.Confluent

import scala.annotation.elidable
import scala.annotation.elidable.CONFIG
import scala.concurrent.ExecutionContext

package object mellite {
  type Cf = Confluent

  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'mllt' - ", Locale.US)
  var showLog         = false
  var showTimelineLog = false

  @elidable(CONFIG) private[mellite] def log(what: => String): Unit =
    if (showLog) println(logHeader.format(new Date()) + what)

  @elidable(CONFIG) private[mellite] def logTimeline(what: => String): Unit =
    if (showTimelineLog) println(s"${logHeader.format(new Date())} <timeline> $what")

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  /** Exception are sometimes swallowed without printing in a transaction. This ensures a print. */
  def ???! : Nothing = {
    val err = new NotImplementedError
    err.printStackTrace()
    throw err
  }
}