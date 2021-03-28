package de.sciss.mellite.impl

final case class Bounds(x: Int, y: Int, width: Int, height: Int) {
  def toVector: Vector[Int] = Vector(x, y, width, height)
}