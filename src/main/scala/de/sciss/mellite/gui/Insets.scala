/*
 *  Insets.scala
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

package de.sciss.mellite.gui

object Insets {
  val empty: Insets = Insets(0, 0, 0, 0)

  implicit object maxHorizontalOrdering extends Ordering[Insets] {
    def compare(a: Insets, b: Insets): Int = {
      val ah = a.maxHorizontal // a.left + a.right
      val bh = b.maxHorizontal // b.left + b.right
      if (ah < bh) -1 else if (ah > bh) +1 else 0
    }
  }
}
final case class Insets(top: Int, left: Int, bottom: Int, right: Int) {
  def maxHorizontal: Int = math.max(left, right)
}