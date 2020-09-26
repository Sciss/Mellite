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

import de.sciss.lucre.Observable
import de.sciss.lucre.impl.ObservableImpl
import de.sciss.lucre.Txn
import de.sciss.lucre.Txn.peer

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.stm.Ref

object NavigationHistory {
  def empty[T <: Txn[T], A]: NavigationHistory[T, A] = new Impl[T, A](Vector.empty)
  
  def apply[T <: Txn[T], A](xs: A*): NavigationHistory[T, A] = new Impl[T, A](xs.toIndexedSeq)
  
  private final class Impl[T <: Txn[T], A](init: Vec[A])
    extends NavigationHistory[T, A] with ObservableImpl[T, Update[T, A]] { nav =>
    
    private[this] val ref   = Ref(init)
    private[this] val _pos  = Ref(init.size)
    
    def current(implicit tx: T): Option[A] = {
      val idx   = position - 1
      val items = ref()
      if (idx < 0 || idx >= items.size) None else Some(items(idx))
    }
    
    def position(implicit tx: T): Int = _pos()
    def position_=(value: Int)(implicit tx: T): Unit = {
      val sz = size
      require(value >= 0 && value <= sz)
      val oldPos = _pos.swap(value)
      if (value != oldPos) {
        fire(Update(nav, position = value, size = sz, current = current))
      }
    }

    def size        (implicit tx: T): Int      = ref().size

    def isEmpty     (implicit tx: T): Boolean  = size == 0
    def nonEmpty    (implicit tx: T): Boolean  = !isEmpty

    def canGoBack   (implicit tx: T): Boolean  = position > 1
    def canGoForward(implicit tx: T): Boolean  = position < size

    def backward()  (implicit tx: T): Unit     = position = position - 1
    def forward ()  (implicit tx: T): Unit     = position = position + 1

    def resetTo(elem: A)(implicit tx: T): Unit =
      update(0, elem)

    def push(elem: A)(implicit tx: T): Unit =
      update(position, elem)

    private def update(index: Int, elem: A)(implicit tx: T): Unit = {
      val newColl = ref.transformAndGet { in =>
        in.take(index) :+ elem
      }
      val newSize = newColl.size
      val newPos  = index + 1
      _pos() = newPos
      fire(Update(nav, position = newPos, size = newSize, current = Some(elem)))
    }
  }
  
  final case class Update[T <: Txn[T], A](nav: NavigationHistory[T, A], position: Int, size: Int, current: Option[A]) {
    def canGoBack   : Boolean = position > 1
    def canGoForward: Boolean = position < size
  }
}
trait NavigationHistory[T <: Txn[T], A] extends Observable[T, NavigationHistory.Update[T, A]] {
  def position              (implicit tx: T): Int
  def position_=(value: Int)(implicit tx: T): Unit
  
  def size        (implicit tx: T): Int

  def current     (implicit tx: T): Option[A]

  def isEmpty     (implicit tx: T): Boolean
  def nonEmpty    (implicit tx: T): Boolean

  def canGoBack   (implicit tx: T): Boolean
  def canGoForward(implicit tx: T): Boolean

  def backward()  (implicit tx: T): Unit
  def forward ()  (implicit tx: T): Unit

  /** Adds element to current positions (and wipes future positions). */
  def push   (elem: A)(implicit tx: T): Unit

  /** Clears contents and sets initial element. */
  def resetTo(elem: A)(implicit tx: T): Unit
}