package de.sciss.lucre

import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Grapheme

/** Cheesy work-around for Lucre #4 -- https://github.com/Sciss/Lucre/issues/4 */
object GraphemeHasIterator {
  implicit class Implicits[S <: Sys[S]](val `this`: Grapheme[S]) extends AnyVal { me =>
    import me.{`this` => gr}
    def iterator(implicit tx: S#Tx): Iterator[(Long, Grapheme.Entry[S])] = new Impl(gr)
  }

  private[this] final class Impl[S <: Sys[S]](gr: Grapheme[S])(implicit tx: S#Tx)
    extends Iterator[(Long, Grapheme.Entry[S])] {

    private[this] var nextTime  = Long.MinValue
    private[this] var nextValue = Option.empty[(Long, Grapheme.Entry[S])]

    advance()

    def advance(): Unit = {
      nextValue = gr.ceil(nextTime).map { entry => entry.key.value -> entry }
      nextValue.foreach { case (currTime, _) => nextTime = currTime + 1 }
    }

    def hasNext: Boolean = nextValue.isDefined

    def next(): (Long, Grapheme.Entry[S]) = {
      val res = nextValue.getOrElse(throw new NoSuchElementException("Exhausted iterator"))
      advance()
      res
    }
  }
}