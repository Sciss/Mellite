package de.sciss.mellite
package gui

object Insets {
  val empty: Insets = Insets(0, 0, 0, 0)

  implicit object maxHorizOrdering extends Ordering[Insets] {
    def compare(a: Insets, b: Insets): Int = {
      val ah = a.maxHoriz // a.left + a.right
      val bh = b.maxHoriz // b.left + b.right
      if (ah < bh) -1 else if (ah > bh) +1 else 0
    }
  }
}
final case class Insets(top: Int, left: Int, bottom: Int, right: Int) {
  def maxHoriz: Int = math.max(left, right)
}