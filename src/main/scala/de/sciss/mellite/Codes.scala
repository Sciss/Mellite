/*
 *  Codes.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.lucre.event.{Targets, Sys}
import de.sciss.serial.{DataOutput, DataInput}
import de.sciss.lucre.synth.expr.BiTypeImpl

object Codes extends BiTypeImpl[Code] {
  final val typeID = 0x20001

  def readValue(in: DataInput): Code = Code.read(in)
  def writeValue(value: Code, out: DataOutput): Unit = value.write(out)

  protected def readTuple[S <: Sys[S]](cookie: Int, in: DataInput, access: S#Acc, targets: Targets[S])
                                      (implicit tx: S#Tx): Codes.ReprNode[S] = {
    sys.error(s"No tuple operations defind for Code ($cookie)")
  }
}