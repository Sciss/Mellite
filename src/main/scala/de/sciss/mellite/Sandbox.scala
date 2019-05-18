package de.sciss.mellite

import scala.language.{higherKinds, implicitConversions}

object Sandbox {
  /*
    the matrix of required support:

    map: Ex[Option[A]] -> Ex[B] -> Ex[Option[B]]
    map: Ex[Seq[A]]    -> Ex[B] -> Ex[Seq[B]]

    map: Ex[Option[A]] -> Act   -> Ex[Option[Act]]
    map: Ex[Seq[A]]    -> Act   -> Ex[Seq[Act]]

    flatMap: Ex[Option[A]] -> Ex[Option[B]] -> Ex[Option[B]]
    flatMap: Ex[Seq[A]] -   > Ex[Option[B]] -> Ex[Seq[B]]
    flatMap: Ex[Seq[A]] -   > Ex[Seq[B]]    -> Ex[Seq[B]]

    which could be represented by five Aux classes

   */

  object CanMap {
    implicit def mapExOpt[B]: CanMap[Option, Ex[B], Ex[Option[B]]]  = new ExOptToExOpt
    implicit def mapExSeq[B]: CanMap[Seq   , Ex[B], Ex[Seq   [B]]]  = new MapExSeq
    implicit def mapOptAct  : CanMap[Option, Act  , Ex[Option[Act]]] = new ExOptToAct2
    implicit def mapSeqAct  : CanMap[Seq   , Act  , Ex[Seq   [Act]]] = ??? //   // ok, know how to do it
  }

  object CanFlatMap {
    implicit def flatMapExOpt   [B]: CanFlatMap[Option, Ex[Option[B]], Ex[Option[B]]]  = new ExOptToExOpt
    implicit def flatMapExSeqOpt[B]: CanFlatMap[Seq   , Ex[Option[B]], Ex[Seq   [B]]]  = new MapExSeqOpt
    implicit def flatMapExSeq   [B]: CanFlatMap[Seq   , Ex[Seq   [B]], Ex[Seq   [B]]]  = new MapExSeq
  }

  private final class ExOptToExOpt[B]
    extends CanMap    [Option, Ex[B]        , Ex[Option[B]]]
      with CanFlatMap [Option, Ex[Option[B]], Ex[Option[B]]]  {

    def map[A](from: Ex[Option[A]], fun: Ex[A] => Ex[B]): Ex[Option[B]] = ???   // ok, know how to do it

    def flatMap[A](from: Ex[Option[A]], fun: Ex[A] => Ex[Option[B]]): Ex[Option[B]] = ???   // ok, know how to do it
  }

  private final class MapExSeq[B]
    extends CanMap[Seq, Ex[B], Ex[Seq[B]]]
    with CanFlatMap[Seq, Ex[Seq[B]], Ex[Seq[B]]] {

    def map[A](from: Ex[Seq[A]], fun: Ex[A] => Ex[B]): Ex[Seq[B]] = ???   // ok, know how to do it

    def flatMap[A](from: Ex[Seq[A]], fun: Ex[A] => Ex[Seq[B]]): Ex[Seq[B]] = ???   // ok, know how to do it
  }

  private final class MapExSeqOpt[B]
    extends CanFlatMap[Seq, Ex[Option[B]], Ex[Seq[B]]] {

    def flatMap[A](from: Ex[Seq[A]], fun: Ex[A] => Ex[Option[B]]): Ex[Seq[B]] = ???   // ok, know how to do it
  }

  //  private final class ExOptToAct
//    extends CanMap    [Option, Act, Act]
//      with CanFlatMap [Option, Act, Act] {
//
//    def map[A](from: Ex[Option[A]], fun: Ex[A] => Act): Act = ???   // ok, know how to do it
//
//    def flatMap[A](from: Ex[Option[A]], fun: Ex[A] => Act): Act = ???   // ok, know how to do it
//  }

  private final class ExOptToAct2
    extends CanMap[Option, Act, Ex[Option[Act]]] {

    def map[A](from: Ex[Option[A]], fun: Ex[A] => Act): Ex[Option[Act]] = ???   // ok, know how to do it?
  }

  trait CanMap[From[_], -B, +To] {
    def map[A](from: Ex[From[A]], fun: Ex[A] => B): To
  }

  trait CanFlatMap[From[_], -B, +To] {
    def flatMap[A](from: Ex[From[A]], fun: Ex[A] => B): To
  }

  trait Ex[+A]
  trait Act
  object Act { def Nop: Act = ??? }

  implicit def liftEx[A](a: A): Ex[A] = ??? // make constant

  implicit def lowerAct(a: Ex[Act]): Act = ???  // wrap in auxiliary act

  implicit def flattenAct1(a: Ex[Option[Act]]): Act = ???  // wrap in auxiliary act
  implicit def flattenAct2(a: Ex[Seq   [Act]]): Act = ???  // wrap in auxiliary act

  trait Trig {
    def ---> (a: Act): Unit
  }

  implicitly[CanMap[Option, Ex[Int], Ex[Option[Int]]]]

  implicit class ExContainerOps[A, From[_]](private val in: Ex[From[A]]) extends AnyVal {
    def map[B, To](fun: Ex[A] => B)(implicit cbf: CanMap[From, B, To]): To =
      cbf.map(in, fun)

    def flatMap[B, To](fun: Ex[A] => B)(implicit cbf: CanFlatMap[From, B, To]): To =
      cbf.flatMap(in, fun)

    def getOrElse[B >: A](default: => Ex[A])(implicit ev: From[A] =:= Option[A]): Ex[B] = ???   // ok, know how to do it
  }

  trait Test {
    def in1: Ex[Option[Int]]
    def in2: Ex[Option[Int]]
    def in3: Ex[Seq[Int]]
    def in4: Ex[Seq[Int]]
    def foo: Ex[Int]
    def bar: Act
    def tr: Trig

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

    // can map and flat-map sequences
    val outSeqEx2: Ex[Seq[Int]] = for {
      _ <- in3
      _ <- in4
    } yield {
      foo
    }

    // can map and flat-map from ex[option[_]] to ex[option[act]]
    val outAct: Ex[Option[Act]] = for {
      _ <- in1
      _ <- in2
    } yield {
      bar
    }

    // can map and flat-map from ex[seq[_]] to ex[seq[act]]
    val outAct2: Ex[Seq[Act]] = for {
      _ <- in3
      _ <- in4
    } yield {
      bar
    }

    tr ---> outAct  // .getOrElse(Act.Nop) // perhaps shortcut: outAct.get, or: outAct.orNop
    tr ---> outAct2 // .getOrElse(Act.Nop) // perhaps shortcut: outAct.get, or: outAct.orNop
  }
}