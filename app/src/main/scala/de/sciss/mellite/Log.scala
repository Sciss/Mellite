package de.sciss.mellite

import de.sciss.log.Logger

// N.B.: Do not put `Logger` instances inside `Mellite`,
// because they would capture `Console.err` early!
object Log {
  final val log       : Logger = new Logger("mllt")
  final val timeline  : Logger = new Logger("mllt timeline")
}
