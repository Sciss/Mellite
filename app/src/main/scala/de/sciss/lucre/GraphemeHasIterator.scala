/*
 *  GraphemeHasIterator.scala
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

package de.sciss.lucre

import de.sciss.proc.Grapheme

/** Cheesy work-around for Lucre #4 -- https://github.com/Sciss/Lucre/issues/4 */
object GraphemeHasIterator {
  implicit class Implicits[T <: Txn[T]](val `this`: Grapheme[T]) extends AnyVal { me =>
    import me.{`this` => gr}
    def iterator(implicit tx: T): Iterator[(Long, Grapheme.Entry[T])] = new Impl(gr)
  }

  private[this] final class Impl[T <: Txn[T]](gr: Grapheme[T])(implicit tx: T)
    extends Iterator[(Long, Grapheme.Entry[T])] {

    private[this] var nextTime  = Long.MinValue
    private[this] var nextValue = Option.empty[(Long, Grapheme.Entry[T])]

    advance()

    def advance(): Unit = {
      nextValue = gr.ceil(nextTime).map { entry => entry.key.value -> entry }
      nextValue.foreach { case (currTime, _) => nextTime = currTime + 1 }
    }

    def hasNext: Boolean = nextValue.isDefined

    def next(): (Long, Grapheme.Entry[T]) = {
      val res = nextValue.getOrElse(throw new NoSuchElementException("Exhausted iterator"))
      advance()
      res
    }
  }
}