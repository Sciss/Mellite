/*
 *  CollectionImpl.scala
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

package de.sciss.mellite.impl.grapheme.tool

import de.sciss.desktop.edit.CompoundEdit
import de.sciss.lucre.expr.LongObj
import de.sciss.lucre.{Txn => LTxn}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{GraphemeCanvas, GraphemeTool, ObjGraphemeView}
import de.sciss.mellite.impl.tool.BasicCollectionTool
import de.sciss.synth.proc.Grapheme
import javax.swing.undo.UndoableEdit

/** A more complete implementation for grapheme tools that process selected views.
  * It implements `commit` by aggregating individual view based
  * commits performed in the abstract method `commitObj`.
  */
trait CollectionImpl[T <: Txn[T], A] extends BasicCollectionTool[T, A, Double, ObjGraphemeView[T]]
  with GraphemeTool[T, A] {

  override protected def canvas: GraphemeCanvas[T]

  def commit(drag: A)(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit] = {
    lazy val parent = canvas.grapheme
    val edits = canvas.selectionModel.iterator.flatMap { childView =>
      val time  = childView.time
      val child = childView.obj
      commitObj(drag)(time = time, child = child, parent = parent)
    } .toList
    val name = edits.headOption.fold("Edit") { ed =>
      val n = ed.getPresentationName
      val i = n.indexOf(' ')
      if (i < 0) n else n.substring(0, i)
    }
    CompoundEdit(edits, name)
  }

  protected def commitObj(drag: A)(time: LongObj[T], child: Obj[T], parent: Grapheme[T])
                         (implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit]
}
