package de.sciss.mellite

/** A simple exception that indicates that only the message string should
  * be shown to the user.
  */
final case class MessageException(message: String) extends RuntimeException(message)
