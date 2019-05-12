package de.sciss.mellite

import de.sciss.lucre.swing.Graph
import de.sciss.lucre.expr.graph._
import de.sciss.lucre.swing.graph._

object ExDnDExample {
  val g: Graph = Graph {
    val tgt   = DropTarget()
    val drop  = tgt.select[TimelineView]
    val timed = drop.value.selectedObjects
    val span  = timed.map(_.span)
    drop.received ---> PrintLn(span.toStr)
    tgt
  }
}
