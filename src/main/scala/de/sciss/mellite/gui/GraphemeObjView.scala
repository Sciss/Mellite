/*
 *  GraphemeObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.IdentifierMap
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.grapheme.{GraphemeObjViewImpl => Impl}
import de.sciss.model.Change
import de.sciss.synth.proc.Grapheme

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Graphics2D

object GraphemeObjView {
  type SelectionModel[S <: stm.Sys[S]] = gui.SelectionModel[S, GraphemeObjView[S]]

  type Map[S <: stm.Sys[S]] = IdentifierMap[S#Id, S#Tx, GraphemeObjView[S]]

  trait Factory extends ObjView.Factory {
    /** Creates a new grapheme view
      */
    def mkGraphemeView[S <: Sys[S]](entry: Grapheme.Entry[S], value: E[S], mode: Mode)
                                   (implicit tx: S#Tx): GraphemeObjView[S]
  }

  def addFactory(f: Factory): Unit = Impl.addFactory(f)

  def factories: Iterable[Factory] = Impl.factories

  def apply[S <: Sys[S]](entry: Grapheme.Entry[S], mode: Mode)
                        (implicit tx: S#Tx): GraphemeObjView[S] =
    Impl(entry = entry, mode = mode)

  final case class InsetsChanged[S <: stm.Sys[S]](view: GraphemeObjView[S], ch: Change[Insets])
    extends ObjView.Update[S]

  trait HasStartLevels[S <: stm.Sys[S]] extends GraphemeObjView[S] {
    def startLevels: Vec[Double]
  }

  final val HandleRadius    = 3.5
  final val HandleDiameter  = 7.0

  final val DefaultInsets   = Insets(4, 4, 4, 4)

  final val ScreenTolerance = 7
}
trait GraphemeObjView[S <: stm.Sys[S]] extends ObjView[S] {
  def entryH: stm.Source[S#Tx, Grapheme.Entry[S]]

  def entry(implicit tx: S#Tx): Grapheme.Entry[S]

  // def id(implicit tx: S#Tx): S#Id

  def time(implicit tx: S#Tx): LongObj[S]

  var timeValue: Long

//  /** If there are no follow up values, this should be set to `Long.MaxValue` */
//  var numFrames: Long

//  var succ: Option[GraphemeObjView[S]]

  def succ_=(opt: Option[GraphemeObjView[S]])(implicit tx: S#Tx): Unit

  def insets: Insets

  def paintBack (g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit
  def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit
}