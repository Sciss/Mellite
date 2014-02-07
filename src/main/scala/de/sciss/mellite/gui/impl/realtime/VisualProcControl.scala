/*
 *  VisualProcControl.scala
 *  (SoundProcesses)
 *
 *  Copyright (c) 2010-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package realtime

import prefuse.controls.ControlAdapter
import javax.swing.SwingUtilities
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import prefuse.Display
import prefuse.visual.{EdgeItem, NodeItem, AggregateItem, VisualItem}
import de.sciss.lucre.stm.Cursor
import de.sciss.lucre.synth.Sys

class VisualProcControl[S <: Sys[S]](cursor: Cursor[S]) extends ControlAdapter {

  private var hoverItem: Option[VisualItem] = None
  private var drag: Option[Drag] = None

  private class Drag(/* val vi: VisualItem,*/ val lastPt: Point2D) {
    var started = false
  }

  def setSmartFixed(vi: VisualItem, state: Boolean): Unit = {
    if (state) {
      vi.setFixed(true)
      return
    }
    //      getData( vi ) match {
    //         case Some( vProc ) if( vProc.proc.anatomy == ProcDiff ) => {
    //            vi.setFixed( true )
    //         }
    //         case _ => {
    vi.setFixed(false)
    //         }
    //      }
  }

  private def getData(vi: VisualItem): Option[VisualProc[S]] = {
    if (vi  .canGet(VisualProc.COLUMN_DATA, classOf     [VisualProc[S]])) {
      Option(vi.get(VisualProc.COLUMN_DATA).asInstanceOf[VisualProc[S]])
    } else None
  }

  override def itemEntered(vi: VisualItem, e: MouseEvent): Unit = {
    //      e.getComponent.setCursor( csrHand )
    hoverItem = Some(vi)
    vi match {
      case ni: NodeItem =>
        setFixed(ni, fixed = true)

      case ei: EdgeItem =>
        setFixed(ei.getSourceItem, fixed = true)
        setFixed(ei.getTargetItem, fixed = true)

      case _ =>
    }
  }

  private def getDisplay(e: MouseEvent) = e.getComponent.asInstanceOf[Display]

  override def itemExited(vi: VisualItem, e: MouseEvent): Unit = {
    hoverItem = None
    vi match {
      case ni: NodeItem =>
        setFixed(ni, fixed = false)

      case ei: EdgeItem =>
        setFixed(ei.getSourceItem, fixed = false)
        setFixed(ei.getTargetItem, fixed = false)

      case _ =>
    }
    //      e.getComponent.setCursor( csrDefault )
  }

  override def itemPressed(vi: VisualItem, e: MouseEvent): Unit = {
    val d = getDisplay(e)
    val displayPt = d.getAbsoluteCoordinate(e.getPoint, null)
    getData(vi).foreach { vp =>
      if (e.getClickCount == 2) {
        //            println( "Jo chuck" )
        cursor.step { implicit tx =>
          val proc = vp.procH() // tx.refresh( vp.staleCursor, vp.proc )
          ProcEditorFrame(proc)(tx, cursor)
        }
      }
    }
    if (!SwingUtilities.isLeftMouseButton(e) || e.isShiftDown) return
    //      if( e.isAltDown() ) {
    //         vi match {
    //
    //         }
    //      }
    val dr = new Drag(displayPt)
    drag = Some(dr)
    if (vi.isInstanceOf[AggregateItem]) setFixed(vi, fixed = true)
  }

  override def itemReleased(vi: VisualItem, e: MouseEvent): Unit = {
    //      vis.getRenderer( vi ) match {
    //         case pr: NuagesProcRenderer => {
    //            val data = vi.get( COL_NUAGES ).asInstanceOf[ VisualData ]
    //            if( data != null ) {
    //               val d          = getDisplay( e )
    //               val displayPt  = d.getAbsoluteCoordinate( e.getPoint, null )
    //               data.update( pr.getShape( vi ))
    //               data.itemReleased( vi, e, displayPt )
    //            }
    //         }
    //         case _ =>
    //      }
    drag.foreach { dr =>
      setFixed(vi, fixed = false)
      drag = None
    }
  }

  override def itemDragged(vi: VisualItem, e: MouseEvent): Unit = {
    val d = getDisplay(e)
    val newPt = d.getAbsoluteCoordinate(e.getPoint, null)
    //      vis.getRenderer( vi ) match {
    //         case pr: NuagesProcRenderer => {
    //            val data       = vi.get( COL_NUAGES ).asInstanceOf[ VisualData ]
    //            if( data != null ) {
    //               data.update( pr.getShape( vi ))
    //               data.itemDragged( vi, e, newPt )
    //            }
    //         }
    //         case _ =>
    //      }
    drag.foreach { dr =>
      if (!dr.started) dr.started = true
      val dx = newPt.getX - dr.lastPt.getX
      val dy = newPt.getY - dr.lastPt.getY
      move(vi, dx, dy)
      dr.lastPt.setLocation(newPt)
    }
  }

  // recursive over aggregate items
  private def setFixed(vi: VisualItem, fixed: Boolean): Unit =
    vi match {
      case ai: AggregateItem =>
        val iter = ai.items()
        while (iter.hasNext) {
          val vi2 = iter.next.asInstanceOf[VisualItem]
          setFixed(vi2, fixed)
        }

      case _ => setSmartFixed(vi, fixed)
    }

  // recursive over aggregate items
  private def move(vi: VisualItem, dx: Double, dy: Double): Unit =
    vi match {
      case ai: AggregateItem =>
        val iter = ai.items()
        while (iter.hasNext) {
          val vi2 = iter.next.asInstanceOf[VisualItem]
          move(vi2, dx, dy)
        }

      case _ =>
        val x = vi.getX
        val y = vi.getY
        vi.setStartX(x)
        vi.setStartY(y)
        vi.setX   (x + dx)
        vi.setY   (y + dy)
        vi.setEndX(x + dx)
        vi.setEndY(y + dy)
    }
}