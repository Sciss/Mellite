/*
 *  NavigationHistory.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.component

import de.sciss.lucre.event.Observable
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.stm.TxnLike.peer

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.Ref

object NavigationHistory {
  def empty[S <: Sys[S], A]: NavigationHistory[S, A] = new Impl[S, A](Vector.empty)
  
  def apply[S <: Sys[S], A](xs: A*): NavigationHistory[S, A] = new Impl[S, A](xs.toIndexedSeq)
  
  private final class Impl[S <: Sys[S], A](init: Vec[A])
    extends NavigationHistory[S, A] with ObservableImpl[S, Update[S, A]] { nav =>
    
    private[this] val ref   = Ref(init)
    private[this] val _pos  = Ref(init.size)
    
    def current(implicit tx: S#Tx): Option[A] = {
      val idx   = position - 1
      val items = ref()
      if (idx < 0 || idx >= items.size) None else Some(items(idx))
    }
    
    def position(implicit tx: S#Tx): Int = _pos()
    def position_=(value: Int)(implicit tx: S#Tx): Unit = {
      val sz = size
      require(value >= 0 && value <= sz)
      val oldPos = _pos.swap(value)
      if (value != oldPos) {
        fire(Update(nav, position = value, size = sz, current = current))
      }
    }

    def size        (implicit tx: S#Tx): Int      = ref().size

    def isEmpty     (implicit tx: S#Tx): Boolean  = size == 0
    def nonEmpty    (implicit tx: S#Tx): Boolean  = !isEmpty

    def canGoBack   (implicit tx: S#Tx): Boolean  = position > 1
    def canGoForward(implicit tx: S#Tx): Boolean  = position < size

    def backward()  (implicit tx: S#Tx): Unit     = position = position - 1
    def forward ()  (implicit tx: S#Tx): Unit     = position = position + 1

    def resetTo(elem: A)(implicit tx: S#Tx): Unit =
      update(0, elem)

    def push(elem: A)(implicit tx: S#Tx): Unit =
      update(position, elem)

    private def update(index: Int, elem: A)(implicit tx: S#Tx): Unit = {
      val newColl = ref.transformAndGet { in =>
        in.take(index) :+ elem
      }
      val newSize = newColl.size
      val newPos  = index + 1
      _pos() = newPos
      fire(Update(nav, position = newPos, size = newSize, current = Some(elem)))
    }
  }
  
  final case class Update[S <: Sys[S], A](nav: NavigationHistory[S, A], position: Int, size: Int, current: Option[A]) {
    def canGoBack   : Boolean = position > 1
    def canGoForward: Boolean = position < size
  }
}
trait NavigationHistory[S <: Sys[S], A] extends Observable[S#Tx, NavigationHistory.Update[S, A]] {
  def position              (implicit tx: S#Tx): Int
  def position_=(value: Int)(implicit tx: S#Tx): Unit
  
  def size        (implicit tx: S#Tx): Int

  def current     (implicit tx: S#Tx): Option[A]

  def isEmpty     (implicit tx: S#Tx): Boolean
  def nonEmpty    (implicit tx: S#Tx): Boolean

  def canGoBack   (implicit tx: S#Tx): Boolean
  def canGoForward(implicit tx: S#Tx): Boolean

  def backward()  (implicit tx: S#Tx): Unit
  def forward ()  (implicit tx: S#Tx): Unit

  /** Adds element to current positions (and wipes future positions). */
  def push   (elem: A)(implicit tx: S#Tx): Unit

  /** Clears contents and sets initial element. */
  def resetTo(elem: A)(implicit tx: S#Tx): Unit
}