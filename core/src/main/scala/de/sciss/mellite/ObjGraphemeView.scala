/*
 *  ObjGraphemeView.scala
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

import de.sciss.lucre.{IdentMap, LongObj, Source, Txn => LTxn}
import de.sciss.lucre.synth.Txn
import de.sciss.mellite
import de.sciss.mellite.GraphemeView.Mode
import de.sciss.mellite.impl.{ObjGraphemeViewImpl => Impl}
import de.sciss.model.Change
import de.sciss.proc.Grapheme

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Graphics2D

object ObjGraphemeView {
  type SelectionModel[T <: LTxn[T]] = mellite.SelectionModel[T, ObjGraphemeView[T]]

  type Map[T <: LTxn[T]] = IdentMap[T, ObjGraphemeView[T]]

  trait Factory extends ObjView.Factory {
    /** Creates a new grapheme view
      */
    def mkGraphemeView[T <: Txn[T]](entry: Grapheme.Entry[T], value: E[T], mode: Mode)
                                   (implicit tx: T): ObjGraphemeView[T]
  }

  def addFactory(f: Factory): Unit = Impl.addFactory(f)

  def factories: Iterable[Factory] = Impl.factories

  def apply[T <: Txn[T]](entry: Grapheme.Entry[T], mode: Mode)
                        (implicit tx: T): ObjGraphemeView[T] =
    Impl(entry = entry, mode = mode)

  final case class InsetsChanged[T <: LTxn[T]](view: ObjGraphemeView[T], ch: Change[Insets])
    extends ObjView.Update[T]

  trait HasStartLevels[T <: LTxn[T]] extends ObjGraphemeView[T] {
    def startLevels: Vec[Double]
  }

  final val HandleRadius    = 3.5
  final val HandleDiameter  = 7.0

  final val DefaultInsets   = Insets(4, 4, 4, 4)

  final val ScreenTolerance = 7
}
trait ObjGraphemeView[T <: LTxn[T]] extends ObjView[T] {
  def entryH: Source[T, Grapheme.Entry[T]]

  def entry(implicit tx: T): Grapheme.Entry[T]

  // def id(implicit tx: T): Ident[T]

  def time(implicit tx: T): LongObj[T]

  var timeValue: Long

//  /** If there are no follow up values, this should be set to `Long.MaxValue` */
//  var numFrames: Long

//  var succ: Option[GraphemeObjView[T]]

  def succ_=(opt: Option[ObjGraphemeView[T]])(implicit tx: T): Unit

  def insets: Insets

  def paintBack (g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit
  def paintFront(g: Graphics2D, gv: GraphemeView[T], r: GraphemeRendering): Unit
}