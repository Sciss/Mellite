/*
 *  Gain.scala
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

package de.sciss.mellite.util

import de.sciss.serial.{DataInput, DataOutput, ImmutableSerializer, Writable}
import de.sciss.synth

object Gain {
  private final val COOKIE = 0x4761   // "Ga"

  def immediate (decibels: Float) = Gain(decibels, normalized = false)
  def normalized(decibels: Float) = Gain(decibels, normalized = true )

  implicit object Serializer extends ImmutableSerializer[Gain] {
    def write(v: Gain, out: DataOutput): Unit = v.write(out)
    def read(in: DataInput): Gain = Gain.read(in)
  }

  def read(in: DataInput): Gain = {
    val cookie      = in.readShort()
    require(cookie == COOKIE, s"Unexpected cookie $cookie (requires $COOKIE)")
    val decibels      = in.readFloat()
    val normalized  = in.readByte() != 0
    Gain(decibels, normalized)
  }
}
final case class Gain(decibels: Float, normalized: Boolean) extends Writable {
  def linear: Float = {
    import synth._
    decibels.dbAmp
  }

  def write(out: DataOutput): Unit = {
    out.writeShort(Gain.COOKIE)
    out.writeFloat(decibels)
    out.writeByte(if (normalized) 1 else 0)
  }
}