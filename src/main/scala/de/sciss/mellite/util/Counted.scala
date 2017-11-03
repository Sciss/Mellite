//package de.sciss.mellite
//package util
//
//import scala.language.implicitConversions
//
//object Counted {
//  implicit def peerOrdering[A](ord: Ordering[A]): Ordering[Counted[A]] = new PeerOrdering(ord)
//
//  private final class PeerOrdering[A](ord: Ordering[A]) extends Ordering[Counted[A]] {
//    def compare(x: Counted[A], y: Counted[A]): Int = ord.compare(x.peer, y.peer)
//  }
//}
//final case class Counted[+A](peer: A)(var count: Int)