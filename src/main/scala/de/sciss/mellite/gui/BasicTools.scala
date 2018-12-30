package de.sciss.mellite.gui

import de.sciss.lucre.stm
import de.sciss.model.Model

object BasicTools {
  final val MinDur = 32
}
trait BasicTools[S <: stm.Sys[S], T, +U] extends Model[U] {
  var currentTool: T
}