/*
 *  CollectionImpl.scala
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

package de.sciss.mellite.gui.impl.grapheme.tool

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.SpanLikeObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.tool.BasicCollectionTool
import de.sciss.mellite.gui.{GraphemeCanvas, GraphemeObjView, GraphemeTool}
import de.sciss.synth.proc.Grapheme
import javax.swing.undo.UndoableEdit

/** A more complete implementation for grapheme tools that process selected marks.
  * It implements `handlePress` to update the mark selection and then
  * for the currently hit mark invoke the `handleSelect` method.
  * It also implements `commit` by aggregating individual mark based
  * commits performed in the abstract method `commitObj`.
  */
trait CollectionImpl[S <: Sys[S], A] extends BasicCollectionTool[S, A, Double, GraphemeObjView[S]]
  with GraphemeTool[S, A] {

  override protected def canvas: GraphemeCanvas[S]

  def commit(drag: A)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    lazy val tl = canvas.grapheme
    val edits = canvas.selectionModel.iterator.flatMap { pv =>
      val span = ??? // pv.span
      val proc = pv.obj
      commitObj(drag)(span, proc, tl)
    } .toList
    val name = edits.headOption.fold("Edit") { ed =>
      val n = ed.getPresentationName
      val i = n.indexOf(' ')
      if (i < 0) n else n.substring(0, i)
    }
    CompoundEdit(edits, name)
  }

  protected def commitObj(drag: A)(span: SpanLikeObj[S], proc: Obj[S], grapheme: Grapheme[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]
}