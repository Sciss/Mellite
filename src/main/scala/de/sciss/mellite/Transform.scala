/*
 *  Transform.scala
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

package de.sciss.mellite

import java.io.File

import de.sciss.processor.ProcessorLike
import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer}
import de.sciss.synth.proc.Code

import scala.concurrent.Future

object Transform {
  private final val COOKIE  = 0x66726D00  // "frm\0"

  case object Unmodified extends Transform

  final case class Coded(source: Code.FileTransform) extends Transform {
    def perform(in: File, out: File, procHandler: ProcessorLike[Any, Any] => Unit = _ => ())
               (implicit compiler: Code.Compiler): Future[Unit] =
      source.execute((in, out, procHandler))
  }

  implicit object Serializer extends ImmutableSerializer[Transform] {
    def write(v: Transform, out: DataOutput): Unit = {
      out.writeInt(COOKIE)
      v match { // "match may not be exhaustive" - not true, scalac bug
        case Unmodified   => out.writeByte(0)
        case Coded(source) =>
          out.writeByte(1)
          Code.serializer.write(source, out)
        case _ => sys.error("WHAT THE HECK SCALA")
      }
    }

    def read(in: DataInput): Transform = {
      val cookie = in.readInt()
      require(cookie == COOKIE, s"Unexpected cookie $cookie (requires $COOKIE)")
      in.readByte() match {
        case 0 => Unmodified
        case 1 =>
          val source = Code.serializer.read(in) match {
            case ft: Code.FileTransform => ft
            case other => sys.error(s"Expected file transform code, but found $other")
          }
          Coded(source)
      }
    }
  }
}
sealed trait Transform