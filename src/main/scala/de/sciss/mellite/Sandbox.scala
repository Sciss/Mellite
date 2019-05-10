package de.sciss.mellite

import scala.language.higherKinds

object Sandbox {
  object CanMap {
    implicit def mapExOpt[B]: CanMap[Option, Ex[B], Ex[Option[B]]]  = new ExOptToExOpt
    implicit def mapExSeq[B]: CanMap[Seq   , Ex[B], Ex[Seq   [B]]]  = new MapExSeq
    implicit def mapAct     : CanMap[Option, Act  , Option[Act]]    = new ExOptToAct
  }

  object CanFlatMap {
    implicit def flatMapExOpt[B]: CanFlatMap[Option, Ex[Option[B]], Ex[Option[B]]]  = new ExOptToExOpt
    implicit def flatMapExSeq[B]: CanFlatMap[Seq   , Ex[Option[B]], Ex[Seq   [B]]]  = new MapExSeq
    implicit def flatMapAct     : CanFlatMap[Option, Option[Act]  , Option[Act]]    = new ExOptToAct
  }

  private final class ExOptToExOpt[B]
    extends CanMap    [Option, Ex[B]        , Ex[Option[B]]]
      with CanFlatMap [Option, Ex[Option[B]], Ex[Option[B]]]  {

    def map[A](from: Ex[Option[A]], fun: Ex[A] => Ex[B]): Ex[Option[B]] = ???   // ok, know how to do it

    def flatMap[A](from: Ex[Option[A]], fun: Ex[A] => Ex[Option[B]]): Ex[Option[B]] = ???   // ok, know how to do it
  }

  private final class MapExSeq[B]
    extends CanMap[Seq, Ex[B], Ex[Seq[B]]]
    with CanFlatMap[Seq, Ex[Option[B]], Ex[Seq[B]]] {

    def map[A](from: Ex[Seq[A]], fun: Ex[A] => Ex[B]): Ex[Seq[B]] = ???   // ok, know how to do it

    def flatMap[A](from: Ex[Seq[A]], fun: Ex[A] => Ex[Option[B]]): Ex[Seq[B]] = ???   // ok, know how to do it
  }

  private final class ExOptToAct
    extends CanMap    [Option, Act        , Option[Act]]
      with CanFlatMap [Option, Option[Act], Option[Act]] {

    def map[A](from: Ex[Option[A]], fun: Ex[A] => Act): Option[Act] = ???   // ok, know how to do it

    def flatMap[A](from: Ex[Option[A]], fun: Ex[A] => Option[Act]): Option[Act] = ???   // ok, know how to do it
  }

  trait CanMap[From[_], -B, +To] {
    def map[A](from: Ex[From[A]], fun: Ex[A] => B): To
  }

  trait CanFlatMap[From[_], -B, +To] {
    def flatMap[A](from: Ex[From[A]], fun: Ex[A] => B): To
  }

//  trait Elem
  trait Ex[+A]  // extends Elem
  trait Act     // extends Elem

  implicitly[CanMap[Option, Ex[Int], Ex[Option[Int]]]]

  implicit class ExContainerOps[A, From[_]](private val in: Ex[From[A]]) extends AnyVal {
    def map[B, To](fun: Ex[A] => B)(implicit cbf: CanMap[From, B, To]): To =
      cbf.map(in, fun)

    def flatMap[B, To](fun: Ex[A] => B)(implicit cbf: CanFlatMap[From, B, To]): To =
      cbf.flatMap(in, fun)
  }

  trait Test {
    def in1: Ex[Option[Int]]
    def in2: Ex[Option[Int]]
    def in3: Ex[Seq[Int]]
    def in4: Ex[Seq[Int]]
    def foo: Ex[Int]
    def bar: Act

    // can map and flat-map options
    val outEx: Ex[Option[Int]] = for {
      _ <- in1
      _ <- in2
    } yield {
      foo
    }

    // can flatMap a seq via options
    val outSeqEx: Ex[Seq[Int]] = for {
      _ <- in3
      _ <- in2
    } yield {
      foo
    }

//    // can map and flat-map sequences
//    val outSeqEx2: Ex[Seq[Int]] = for {
//      _ <- in3
//      _ <- in4
//    } yield {
//      foo
//    }

    // can map and flat-map from ex[option[_]] to option[act]
    val outAct: Option[Act] = for {
      _ <- in1
      _ <- in2
    } yield {
      bar
    }
  }
}
