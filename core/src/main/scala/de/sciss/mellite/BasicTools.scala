/*
 *  BasicTools.scala
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

import de.sciss.lucre.stm
import de.sciss.model.Model

object BasicTools {
  final val MinDur = 32
}
trait BasicTools[S <: stm.Sys[S], T, +U] extends Model[U] {
  var currentTool: T
}