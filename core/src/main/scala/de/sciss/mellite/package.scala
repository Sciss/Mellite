/*
 *  package.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss

import de.sciss.mellite.impl.RecursionImpl
import de.sciss.synth.proc.Confluent
import java.text.SimpleDateFormat
import java.util.{Date, Locale}
import scala.annotation.elidable
import scala.annotation.elidable.CONFIG
import scala.concurrent.ExecutionContext

package object mellite {
  type Cf = Confluent

  private lazy val logHeader = new SimpleDateFormat("[d MMM yyyy, HH:mm''ss.SSS] 'Mellite' - ", Locale.US)
  var showLog         = false
  var showTimelineLog = false

  @elidable(CONFIG) private[mellite] def log(what: => String): Unit =
    if (showLog) println(logHeader.format(new Date()) + what)

  @elidable(CONFIG) private[mellite] def logTimeline(what: => String): Unit =
    if (showTimelineLog) println(s"${logHeader.format(new Date())} <timeline> $what")

  implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def initTypes(): Unit = {
    de.sciss.lucre.synth.expr.initTypes()
    RecursionImpl.ElemImpl
  }
}