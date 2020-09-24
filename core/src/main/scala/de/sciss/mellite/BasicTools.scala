/*
 *  BasicTools.scala
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

import de.sciss.lucre.{Txn => LTxn}
import de.sciss.model.Model

object BasicTools {
  final val MinDur = 32
}
trait BasicTools[T <: LTxn[T], TL, +U] extends Model[U] {
  var currentTool: TL
}