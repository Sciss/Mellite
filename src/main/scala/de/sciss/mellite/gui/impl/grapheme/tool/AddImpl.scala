/*
 *  AddImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.gui.impl.grapheme.tool

import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.{Cursor => AWTCursor}

import de.sciss.icons.raphael
import de.sciss.lucre.expr.DoubleObj
import de.sciss.lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.BasicTool.Adjust
import de.sciss.mellite.gui.Shapes
import de.sciss.mellite.gui.edit.EditGraphemeInsertObj
import de.sciss.mellite.{BasicTool, GUI, GraphemeCanvas, GraphemeTool, ObjGraphemeView}
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.Curve
import de.sciss.synth.proc.{CurveObj, EnvSegment}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.swing.Component

final class AddImpl[S <: Sys[S]](val canvas: GraphemeCanvas[S])
  extends GraphemeTool[S, GraphemeTool.Add] with ModelImpl[BasicTool.Update[GraphemeTool.Add]] {

  type Y      = Double
  type Child  = ObjGraphemeView[S]

  def defaultCursor: AWTCursor  = AWTCursor.getPredefinedCursor(AWTCursor.CROSSHAIR_CURSOR)
  def name                      = "Add Envelope Segment"
//  val icon: Icon                = GUI.iconNormal(raphael.Shapes.Plus)
  val icon: Icon                = GUI.iconNormal(Shapes.plus(raphael.Shapes.Connect))

  private[this] val mia = new MouseAdapter {
    override def mousePressed(e: MouseEvent): Unit = {
      e.getComponent.requestFocus()
      val pos       = canvas.screenToFrame(e.getX).toLong
      val modelY    = canvas.screenToModelPos(e.getY)
      val childOpt  = canvas.findChildView(pos, modelY)
      handlePress(e, modelY, pos, childOpt)
    }
  }

  private def handlePress(e: MouseEvent, modelY: Y, pos: Long,
                            childOpt: Option[Child]): Unit = {
    // XXX TODO --- do not hard-code tpe
    dispatch(Adjust(GraphemeTool.Add(time = pos, modelY = modelY, tpe = EnvSegment.Obj)))
  }

  def install(component: Component): Unit = {
    component.peer.addMouseListener(mia)
    component.cursor = defaultCursor
  }

  def uninstall(component: Component): Unit = {
    component.peer.removeMouseListener(mia)
    component.cursor = null
  }

  /** Called after the end of a mouse drag gesture. If this constitutes a
    * valid edit, the method should return the resulting undoable edit.
    *
    * @param drag   the last editing state
    * @param cursor the cursor that might be needed to construct the undoable edit
    * @return either `Some` edit or `None` if the action does not constitute an
    *         edit or the edit parameters are invalid.
    */
  def commit(drag: GraphemeTool.Add)(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] =
    canvas.grapheme.modifiableOption.map { grMod =>
      assert (drag.tpe.typeId == EnvSegment.Obj.typeId)
      val startLevel  = DoubleObj.newVar[S](drag.modelY)
      val curve       = CurveObj .newVar[S](Curve.lin)
      val elem        = EnvSegment.Obj.ApplySingle(startLevel, curve)
      EditGraphemeInsertObj(name, grMod, time = drag.time, elem = elem)
    }
}
