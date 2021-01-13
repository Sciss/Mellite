/*
 *  Log.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.log.Logger

// N.B.: Do not put `Logger` instances inside `Mellite`,
// because they would capture `Console.err` early!
object Log {
  final val log       : Logger = new Logger("mllt")
  final val timeline  : Logger = new Logger("mllt timeline")
}
