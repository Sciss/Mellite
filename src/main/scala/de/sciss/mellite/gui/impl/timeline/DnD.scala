/*
 *  DnD.scala
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

package de.sciss.mellite
package gui
package impl
package timeline

import java.awt.Point
import java.awt.datatransfer.{DataFlavor, Transferable}
import java.awt.dnd.{DropTarget, DropTargetAdapter, DropTargetDragEvent, DropTargetDropEvent, DropTargetEvent}

import javax.swing.TransferHandler._
import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.Desktop
import de.sciss.equal.Implicits._
import de.sciss.file._
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.gui.DragAndDrop.Flavor
import de.sciss.span.Span
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.{AudioCue, Proc, TimeRef, Universe}

import scala.swing.Component
import scala.util.Try

object DnD {
  sealed trait Drag[S <: Sys[S]] {
    def universe: Universe[S]
    // def source: stm.Source[S#Tx, Element[S]]
  }
  sealed trait AudioDragLike[S <: Sys[S]] extends Drag[S] {
    def selection: Span
  }
  final case class AudioDrag[S <: Sys[S]](universe: Universe[S], source: stm.Source[S#Tx, AudioCue.Obj[S]],
                                          selection: Span)
    extends AudioDragLike[S]

  //  final case class IntDrag [S <: Sys[S]](document: File, source: stm.Source[S#Tx, Obj.T[S, IntObj  ]]) extends Drag[S]
  //  final case class CodeDrag[S <: Sys[S]](document: File, source: stm.Source[S#Tx, Code.Obj[S]]) extends Drag[S]
  //  final case class ProcDrag[S <: Sys[S]](document: File, source: stm.Source[S#Tx, Proc[S]]) extends Drag[S]

  final case class GlobalProcDrag[S <: Sys[S]](universe: Universe[S], source: stm.Source[S#Tx, Proc[S]])
    extends Drag[S]

  final case class ObjectDrag[S <: SSys[S]](universe: Universe[S], view: ObjView[S]) extends Drag[S]

  /** Drag and Drop from Eisenkraut */
  final case class ExtAudioRegionDrag[S <: Sys[S]](universe: Universe[S], file: File, selection: Span)
    extends AudioDragLike[S]

  final case class Drop[S <: Sys[S]](frame: Long, y: Int, drag: Drag[S])

  final val flavor: Flavor[Drag[_]] = DragAndDrop.internalFlavor[Drag[_]]
}
trait DnD[S <: SSys[S]] {
  _: Component =>

  import DnD._

  protected def universe: Universe[S]
  protected def timelineModel: TimelineModel

  protected def updateDnD(drop: Option[Drop[S]]): Unit
  protected def acceptDnD(drop:        Drop[S] ): Boolean

  private object Adaptor extends DropTargetAdapter {
    override def dragEnter(e: DropTargetDragEvent): Unit = process(e)
    override def dragOver (e: DropTargetDragEvent): Unit = process(e)
    override def dragExit (e: DropTargetEvent    ): Unit = updateDnD(None)

    private def abortDrag (e: DropTargetDragEvent): Unit = {
      updateDnD(None)
      e.rejectDrag()
    }

    private def mkDrop(d: DnD.Drag[S], loc: Point): Drop[S] = {
      val vis   = timelineModel.visible
      val w     = peer.getWidth
      val frame = (loc.x.toDouble / w * vis.length + vis.start).toLong
      val y     = loc.y
      Drop(frame = frame, y = y, drag = d)
    }

    private[this] var lastFile: AudioCue = _

    private def mkExtStringDrag(t: Transferable, isDragging: Boolean): Option[DnD.ExtAudioRegionDrag[S]] = {
      // stupid OS X doesn't give out the string data before drop actually happens
      if (isDragging && !Desktop.isLinux) {
        return Some(ExtAudioRegionDrag(universe, file(""), Span(0, 0)))
      }

      val data  = t.getTransferData(DataFlavor.stringFlavor)
      val str   = data.toString
      val arr   = str.split(java.io.File.pathSeparator)
      if (arr.length == 3) {
        Try {
          val path = file(arr(0))
          // cheesy caching so we read spec only once during drag
          if (lastFile == null || (lastFile.artifact !== path)) {
            val spec = AudioFile.readSpec(path)
            lastFile = AudioCue(path, spec, 0L, 1.0)
          }
          val ratio = TimeRef.SampleRate / lastFile.spec.sampleRate
          val start = (arr(1).toLong * ratio + 0.5).toLong
          val stop  = (arr(2).toLong * ratio + 0.5).toLong
          val span  = Span(start, stop)
          ExtAudioRegionDrag[S](universe, path, span)
        } .toOption
      } else None
    }

    private def acceptAndUpdate(e: DropTargetDragEvent, drag: Drag[S]): Unit = {
      val loc   = e.getLocation
      val drop  = mkDrop(drag, loc)
      updateDnD(Some(drop))
      lastFile  = null
      e.acceptDrag(e.getDropAction) // COPY
    }

    private def isSupported(t: Transferable): Boolean =
      t.isDataFlavorSupported(DnD.flavor) ||
      t.isDataFlavorSupported(ListObjView.Flavor) ||
      t.isDataFlavorSupported(DataFlavor.stringFlavor)

    private def mkDrag(t: Transferable, isDragging: Boolean): Option[Drag[S]] =
      if (t.isDataFlavorSupported(DnD.flavor)) {
        t.getTransferData(DnD.flavor) match {
          case d: DnD.Drag[_] if d.universe == universe => Some(d.asInstanceOf[DnD.Drag[S]])
          case _ => None
        }
      } else if (t.isDataFlavorSupported(ListObjView.Flavor)) {
        t.getTransferData(ListObjView.Flavor) match {
          case ListObjView.Drag(u, view) if u == universe =>
            Some(DnD.ObjectDrag(universe, view.asInstanceOf[ObjView[S]]))
          case _ => None
        }

      } else if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        mkExtStringDrag(t, isDragging = isDragging)

      } else {
        None
      }

    private def process(e: DropTargetDragEvent): Unit = {
      val d = mkDrag(e.getTransferable, isDragging = true)
      // println(s"mkDrag = $d")
      d match {
        case Some(drag) => acceptAndUpdate(e, drag)
        case _          => abortDrag(e)
      }
    }

    def drop(e: DropTargetDropEvent): Unit = {
      updateDnD(None)

      val t = e.getTransferable
      if (!isSupported(t)) {
        e.rejectDrop()
        return
      }
      e.acceptDrop(e.getDropAction)
      mkDrag(t, isDragging = false) match {
        case Some(drag) =>
          val loc     = e.getLocation
          val drop    = mkDrop(drag, loc)
          val success = acceptDnD(drop)
          e.dropComplete(success)

        case _ => e.rejectDrop()
      }
    }
  }

  new DropTarget(peer, COPY | LINK, Adaptor)
}
