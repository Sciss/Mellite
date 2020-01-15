/*
 *  MessageException.scala
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

package de.sciss.mellite

/** A simple exception that indicates that only the message string should
  * be shown to the user.
  */
final case class MessageException(message: String) extends RuntimeException(message)
