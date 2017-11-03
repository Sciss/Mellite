/*
 *  GraphemeObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{IdentifierMap, Obj}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.mellite.gui.impl.grapheme.{GraphemeObjViewImpl => Impl}
import de.sciss.model.Change

import scala.swing.Graphics2D

object GraphemeObjView {
  type SelectionModel[S <: stm.Sys[S]] = gui.SelectionModel[S, GraphemeObjView[S]]

  type Map[S <: stm.Sys[S]] = IdentifierMap[S#ID, S#Tx, GraphemeObjView[S]]

  trait Factory extends ObjView.Factory {
    /** Creates a new grapheme view
      *
      * @param time     the time position on the grapheme
      * @param obj      the object placed on the grapheme
      */
    def mkGraphemeView[S <: Sys[S]](time: LongObj[S], obj: E[S], mode: Mode)(implicit tx: S#Tx): GraphemeObjView[S]
  }

  def addFactory(f: Factory): Unit = Impl.addFactory(f)

  def factories: Iterable[Factory] = Impl.factories

  def apply[S <: Sys[S]](time: LongObj[S], value: Obj[S], mode: Mode)(implicit tx: S#Tx): GraphemeObjView[S] =
    Impl(time, value, mode)

  final case class InsetsChanged[S <: stm.Sys[S]](view: GraphemeObjView[S], ch: Change[Insets])
    extends ObjView.Update[S]
}
trait GraphemeObjView[S <: stm.Sys[S]] extends ObjView[S] {
  def timeH: stm.Source[S#Tx, LongObj[S]]

  def time(implicit tx: S#Tx): LongObj[S]

  // def id(implicit tx: S#Tx): S#ID

  var timeValue: Long

  def insets: Insets

  def paintBack (g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit
  def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit
}