/*
 *  GraphemeModel.scala
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

import de.sciss.model.impl.ModelImpl
import de.sciss.model.{Change, Model}

object GraphemeModel {
  sealed trait Update { def model: GraphemeModel }
  final case class YAxisRange(model: GraphemeModel, range: Change[DoubleSpan]) extends Update

  type Listener = Model.Listener[Update]

  trait Modifiable extends GraphemeModel {
    var yAxisRange: DoubleSpan

    def modifiableOption: Option[GraphemeModel.Modifiable] = Some(this)
  }

  def apply(yAxisRange: DoubleSpan = DoubleSpan(0.0, 1.0)): Modifiable =
    new Impl(yAxisRange0 = yAxisRange)

  private final class Impl(yAxisRange0: DoubleSpan)
    extends GraphemeModel.Modifiable with ModelImpl[GraphemeModel.Update] {

    private[this] var _yAxisRange = yAxisRange0

    def yAxisRange: DoubleSpan = _yAxisRange
    def yAxisRange_=(value: DoubleSpan): Unit = {
      if (_yAxisRange != value) {
        _yAxisRange = value
        val ch = Change(_yAxisRange, value)
        dispatch(YAxisRange(this, ch))
      }
    }
  }
}
trait GraphemeModel extends Model[GraphemeModel.Update]{
  def yAxisRange: DoubleSpan

  def modifiableOption: Option[GraphemeModel.Modifiable]
}