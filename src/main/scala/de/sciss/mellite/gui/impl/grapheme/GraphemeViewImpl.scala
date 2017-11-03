/*
 *  GraphemeViewImpl.scala
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
package impl
package grapheme

import java.awt
import java.awt.{Font, Graphics2D, LinearGradientPaint, RenderingHints}
import java.util.Locale
import javax.swing.{JComponent, UIManager}

import de.sciss.audiowidgets.TimelineModel
import de.sciss.audiowidgets.impl.TimelineModelImpl
import de.sciss.desktop
import de.sciss.desktop.{UndoManager, Window}
import de.sciss.icons.raphael
import de.sciss.lucre.bitemp.BiPin
import de.sciss.lucre.stm.{Cursor, Disposable, TxnLike}
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.{GraphemeHasIterator, stm}
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.model.Change
import de.sciss.span.Span
import de.sciss.synth.proc.{Grapheme, TimeRef, Workspace}

import scala.collection.immutable.{SortedMap => ISortedMap}
import scala.concurrent.stm.{Ref, TMap, TSet}
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, BoxPanel, Component, Orientation}

object GraphemeViewImpl {
  private val colrBg              = awt.Color.darkGray
  private val colrRegionOutline   = new awt.Color(0x68, 0x68, 0x68)
  private val colrRegionOutlineSel= awt.Color.blue
  private val pntRegionBg         = new LinearGradientPaint(0f, 1f, 0f, 62f,
    Array[Float](0f, 0.23f, 0.77f, 1f), Array[awt.Color](new awt.Color(0x5E, 0x5E, 0x5E), colrRegionOutline,
      colrRegionOutline, new awt.Color(0x77, 0x77, 0x77)))
  private val pntRegionBgSel       = new LinearGradientPaint(0f, 1f, 0f, 62f,
    Array[Float](0f, 0.23f, 0.77f, 1f), Array[awt.Color](new awt.Color(0x00, 0x00, 0xE6), colrRegionOutlineSel,
      colrRegionOutlineSel, new awt.Color(0x1A, 0x1A, 0xFF)))

  private val DEBUG   = false

  private val NoMove  = TrackTool.Move(deltaTime = 0L, deltaTrack = 0, copy = false)

  import de.sciss.mellite.{logTimeline => logT}

//  private type EntryProc[S <: Sys[S]] = BiGroup.Entry[S, Proc[S]]

  def apply[S <: Sys[S]](obj: Grapheme[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         undo: UndoManager): GraphemeView[S] = {
    val sampleRate      = TimeRef.SampleRate
    val tlm             = new TimelineModelImpl(Span(0L, (sampleRate * 60 * 60).toLong), sampleRate)
    tlm.visible         = Span(0L, (sampleRate * 60 * 2).toLong)
    val grapheme        = obj
    val graphemeH       = tx.newHandle(obj)
    var disposables     = List.empty[Disposable[S#Tx]]
    val viewMap         = TMap.empty[Long, GraphemeObjView[S]] // tx.newInMemoryIDMap[GraphemeObjView[S]]
    val selectionModel  = SelectionModel[S, GraphemeObjView[S]]
    val grView          = new Impl[S](graphemeH, viewMap, tlm, selectionModel)

    import GraphemeHasIterator._

    grapheme.iterator.foreach { case (time, entry) =>
      if (DEBUG) println(s"ADD $time / ${TimeRef.framesToSecs(time)}, ${entry.value}")
      grView.objAdded(time, entry, repaint = false)
    }

    val obsGrapheme = grapheme.changed.react { implicit tx => upd =>
      upd.changes.foreach {
        case BiPin.Added  (time, entry) =>
          if (DEBUG) println(s"Added   $time, $entry")
          grView.objAdded(time, entry, repaint = true)

        case BiPin.Removed(time, entry) =>
          if (DEBUG) println(s"Removed $time, $entry")
          grView.objRemoved(time, entry)

        case BiPin.Moved  (timeChange, entry) =>
          if (DEBUG) println(s"Moved   $entry, $timeChange")
          grView.objMoved(entry, timeCh = timeChange)
      }
    }
    disposables ::= obsGrapheme

    grView.disposables.set(disposables)(tx.peer)

    deferTx(grView.guiInit())
    grView
  }

  private final class Impl[S <: Sys[S]](val graphemeH     : stm.Source[S#Tx, Grapheme[S]],
                                        val viewMap       : TMap[Long, GraphemeObjView[S]],
                                        val timelineModel : TimelineModel,
                                        val selectionModel: SelectionModel[S, GraphemeObjView[S]])
                                       (implicit val workspace: Workspace[S], val cursor: Cursor[S],
                                        val undoManager: UndoManager)
    extends GraphemeActions[S]
      with GraphemeView[S]
      with ComponentHolder[Component] {

    impl =>

    import cursor.step

    private[this] var viewRange     = ISortedMap.empty[Long, GraphemeObjView[S]]
    private[this] val viewSet       = TSet.empty[GraphemeObjView[S]]
//    private[this] val viewMaxHoriz  = mutable.PriorityQueue.empty[Insets](Insets.maxHorizOrdering)
    private[this] var viewMaxHoriz  = ISortedMap.empty[Int, Int] // maxHoriz to count

    private[this] var canvasView: View    = _

    val disposables           = Ref(List.empty[Disposable[S#Tx]])

    def mode: Mode = Mode.TwoDim

//    private lazy val toolCursor   = TrackTool.cursor  [S](canvasView)
//    private lazy val toolMove     = TrackTool.move    [S](canvasView)

    def grapheme  (implicit tx: S#Tx): Grapheme[S] = graphemeH()
    def plainGroup(implicit tx: S#Tx): Grapheme[S] = grapheme

    def window: Window = component.peer.getClientProperty("de.sciss.mellite.Window").asInstanceOf[Window]

    def canvasComponent: Component = canvasView.canvasComponent

    def dispose()(implicit tx: S#Tx): Unit = {
      deferTx {
        viewRange = ISortedMap.empty
      }
      disposables.swap(Nil)(tx.peer).foreach(_.dispose())
      viewSet.foreach(_.dispose())(tx.peer)
      clearSet(viewSet)
      // this is already included in `disposables`:
      // viewMap.dispose()
    }

    private def clearSet[A](s: TSet[A])(implicit tx: S#Tx): Unit =
      s.retain(_ => false)(tx.peer) // no `clear` method

    def guiInit(): Unit = {
      canvasView = new View

      val actionAttr: Action = Action(null) {
        withSelection { implicit tx =>
          seq => {
            seq.foreach { view =>
              AttrMapFrame(view.obj)
            }
            None
          }
        }
      }

      actionAttr.enabled = false
      val ggAttr = GUI.toolButton(actionAttr, raphael.Shapes.Wrench, "Attributes Editor")
      ggAttr.focusable = false

      val transportPane = new BoxPanel(Orientation.Horizontal) {
        contents ++= Seq(
          HStrut(4),
//          TrackTools.palette(canvasView.trackTools, Vector(
//            toolCursor, toolMove, toolResize, toolGain, toolFade /* , toolSlide*/ ,
//            toolMute, toolAudition, toolFunction, toolPatch)),
//          HStrut(4),
          ggAttr,
          HGlue
        )
      }

      selectionModel.addListener {
        case _ =>
          val hasSome = selectionModel.nonEmpty
          actionAttr              .enabled = hasSome
//          actionMoveObjectToCursor.enabled = hasSome
      }

//      timelineModel.addListener {
//        case TimelineModel.Selection(_, span) if span.before.isEmpty != span.now.isEmpty =>
//          val hasSome = span.now.nonEmpty
//          actionClearSpan .enabled = hasSome
//          actionRemoveSpan.enabled = hasSome
//      }

      val pane2 = canvasView.component
//      pane2.dividerSize         = 4
//      pane2.border              = null
//      pane2.oneTouchExpandable  = true

      val pane = new BorderPanel {
        import BorderPanel.Position._
        layoutManager.setVgap(2)
        add(transportPane, North )
        add(pane2        , Center)
        // add(ggTrackPos   , East  )
        // add(globalView.component, West  )
      }

      component = pane
      // DocumentViewHandler.instance.add(this)
    }

    private def repaintAll(): Unit = canvasView.canvasComponent.repaint()

    private def addInsets(i: Insets): Unit = {
      val h = i.maxHoriz
      val c = viewMaxHoriz.getOrElse(h, 0) + 1
      viewMaxHoriz += h -> c
    }

    private def removeInsets(i: Insets): Unit = {
      val h = i.maxHoriz
      val c = viewMaxHoriz(h) - 1
      if (c == 0) viewMaxHoriz -= h else viewMaxHoriz += h -> c
    }

    def objAdded(time: Long, entry: Grapheme.Entry[S], repaint: Boolean)(implicit tx: S#Tx): Unit = {
      import TxnLike.peer
      logT(s"objAdded($time / ${TimeRef.framesToSecs(time)}, $entry)")
      // entry.span
      // val proc = entry.value

      // val pv = ProcView(entry, viewMap, scanMap)
      val view = GraphemeObjView(entry.key, entry.value, mode)
      viewMap.put(time, view)
      viewSet.add(view)(tx.peer)

      def doAdd(): Unit = {
        viewRange += time -> view
        addInsets(view.insets)
        if (repaint) repaintAll()    // XXX TODO: optimize dirty rectangle
      }

      if (repaint)
        deferTx(doAdd())
      else
        doAdd()

      // XXX TODO -- do we need to remember the disposable?
      view.react { implicit tx => {
        case ObjView.Repaint(_) => objUpdated(view)
        case GraphemeObjView.InsetsChanged(_, Change(before, now)) if before.maxHoriz != now.maxHoriz =>
          deferTx {
            removeInsets(before)
            addInsets   (now   )
            repaintAll()    // XXX TODO: optimize dirty rectangle
          }
        case _ =>
      }}
    }

    private def warnViewNotFound(action: String, entry: Grapheme.Entry[S]): Unit =
      Console.err.println(s"Warning: Grapheme - $action. View for object $entry not found.")

    def objRemoved(time: Long, entry: Grapheme.Entry[S])(implicit tx: S#Tx): Unit = {
      import TxnLike.peer
      logT(s"objRemoved($time, $entry)")
      viewMap.remove(time).fold {
        warnViewNotFound("remove", entry)
      } { view =>
        viewSet.remove(view)(tx.peer)
        deferTx {
          viewRange -= time
          removeInsets(view.insets)
          repaintAll() // XXX TODO: optimize dirty rectangle
        }
        view.dispose()
      }
    }

    // insignificant changes are ignored, therefore one can just move the span without the track
    // by using trackCh = Change(0,0), and vice versa
    def objMoved(entry: Grapheme.Entry[S], timeCh: Change[Long])
                (implicit tx: S#Tx): Unit = {
      import TxnLike.peer
      logT(s"objMoved(${timeCh.before} / ${TimeRef.framesToSecs(timeCh.before)} -> ${timeCh.now} / ${TimeRef.framesToSecs(timeCh.now)}, $entry)")
      viewMap.remove(timeCh.before).fold {
        warnViewNotFound("move", entry)
      } { view =>
        viewMap.put(timeCh.now, view)
        deferTx {
          viewRange -= timeCh.before
          view.timeValue = timeCh .now
          viewRange += timeCh.now -> view
          repaintAll()  // XXX TODO: optimize dirty rectangle
        }
      }
    }

    private def objUpdated(view: GraphemeObjView[S]): Unit = {
      //      if (view.isGlobal)
      //        globalView.updated(view)
      //      else
      repaintAll() // XXX TODO: optimize dirty rectangle
    }

    private final class View extends GraphemeCanvas[S] {
      canvasImpl =>

      // import AbstractGraphemeView._
      def timelineModel : TimelineModel                         = impl.timelineModel
      def selectionModel: SelectionModel[S, GraphemeObjView[S]] = impl.selectionModel
      def grapheme(implicit tx: S#Tx): Grapheme[S]              = impl.plainGroup

      def findView(pos: Long): Option[GraphemeObjView[S]] =
        viewRange.range(pos, Long.MaxValue).headOption.map(_._2)

//      def findRegions(r: TrackTool.Rectangular): Iterator[GraphemeObjView[S]] = {
//        val views = intersect(r.span)
//        views.filter(pv => pv.trackIndex < r.trackIndex + r.trackHeight && (pv.trackIndex + pv.trackHeight) > r.trackIndex)
//      }

      protected def commitToolChanges(value: Any): Unit = {
        logT(s"Commit tool changes $value")
        val editOpt = step { implicit tx =>
          value match {
//            case t: TrackTool.Cursor    => toolCursor commit t
//            case t: TrackTool.Move      =>
//              val res = toolMove.commit(t)
//              res
            case _ => None
          }
        }
        editOpt.foreach(undoManager.add)
      }

      private var _toolState    = Option.empty[Any]
      private var moveState     = NoMove

      protected def toolState: Option[Any] = _toolState
      protected def toolState_=(state: Option[Any]): Unit = {
        _toolState    = state
        moveState     = NoMove

        state.foreach {
          case s: TrackTool.Move => moveState = s
          case _ =>
        }
      }

      object canvasComponent extends Component /* with DnD[S] */ /* with sonogram.PaintController */ {
        protected def graphemeModel: TimelineModel  = impl.timelineModel
        protected def workspace: Workspace[S]       = impl.workspace

        // private var currentDrop = Option.empty[DnD.Drop[S]]

        font = {
          val f = UIManager.getFont("Slider.font", Locale.US)
          if (f != null) f.deriveFont(math.min(f.getSize2D, 9.5f)) else new Font("SansSerif", Font.PLAIN, 9)
        }

        preferredSize = {
          val b = desktop.Util.maximumWindowBounds
          (b.width >> 1, b.height >> 1)
        }

//        protected def updateDnD(drop: Option[DnD.Drop[S]]): Unit = {
//          currentDrop = drop
//          repaint()
//        }
//
//        protected def acceptDnD(drop: DnD.Drop[S]): Boolean = performDrop(drop)

        def imageObserver: JComponent = peer

        override protected def paintComponent(g: Graphics2D): Unit = {
          super.paintComponent(g)
          val w = peer.getWidth
          val h = peer.getHeight
          g.setColor(colrBg) // g.setPaint(pntChecker)
          g.fillRect(0, 0, w, h)

          val total     = graphemeModel.bounds
          val clipOrig  = g.getClip
          val cr        = clipOrig.getBounds
          val visStart  = screenToFrame(cr.x).toLong
          val visStop   = screenToFrame(cr.x + cr.width).toLong + 1 // plus one to avoid glitches
          val sel       = selectionModel

          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

          val maxHorizF = if (viewMaxHoriz.isEmpty) 0L else screenToFrames(viewMaxHoriz.lastKey).toLong

          // warning: iterator, we need to traverse twice!
          viewRange.range(visStart - maxHorizF, visStop + maxHorizF).foreach { case (_, view) =>
            val selected  = sel.contains(view)

            def drawProc(start: Long, x1: Int, x2: Int, move: Long): Unit = {
              // val pTrk  = if (selected) math.max(0, view.trackIndex + moveState.deltaTrack) else view.trackIndex
              val py    = 0 // trackToScreen(pTrk)
              val px    = x1
              val pw    = x2 - x1
              val ph    = peer.getHeight // trackToScreen(pTrk + view.trackHeight) - py

              // clipped coordinates
              val px1C    = math.max(px + 1, cr.x - 2)
              val px2C    = math.min(px + pw, cr.x + cr.width + 3)
              if (px1C < px2C) {  // skip this if we are not overlapping with clip

                g.translate(px, py)
                g.setColor(if (selected) colrRegionOutlineSel else colrRegionOutline)
                g.fillRoundRect(0, 0, pw, ph, 5, 5)
                g.setPaint(if (selected) pntRegionBgSel else pntRegionBg)
                g.fillRoundRect(1, 1, pw - 2, ph - 2, 4, 4)
                g.translate(-px, -py)

                g.setColor(colrBg)
                g.drawLine(px - 1, py, px - 1, py + ph - 1) // better distinguish directly neighbouring views

//                val hndl = 0
//                val innerH  = ph - (hndl + 1)
//                val innerY  = py + hndl
//                g.clipRect(px + 1, innerY, pw - 2, innerH)
//                g.setClip(clipOrig)
              }
            }

            def adjustStart(start: Long): Long =
              if (selected) {
                val dt0 = moveState.deltaTime // + resizeState.deltaStart
                if (dt0 >= 0) dt0 else {
                  math.max(-(start - total.start), dt0)
                }
              } else 0L

            def adjustStop(stop: Long): Long =
              if (selected) {
                val dt0 = moveState.deltaTime // + resizeState.deltaStop
                if (dt0 >= 0) dt0 else {
                  math.max(-(stop - total.start + TimelineView.MinDur), dt0)
                }
              } else 0L

            def adjustMove(start: Long): Long =
              if (selected) {
                val dt0 = moveState.deltaTime
                if (dt0 >= 0) dt0 else {
                  math.max(-(start - total.start), dt0)
                }
              } else 0L

//            view.spanValue match {
//              case Span(start, stop) =>
            val start = view.timeValue
            val stop  = start
                val dStart    = adjustStart(start)
                val dStop     = adjustStop (stop )
                val newStart  = start + dStart
                val newStop   = math.max(newStart + TimelineView.MinDur, stop + dStop)
                val x1        = frameToScreen(newStart).toInt
                val x2        = frameToScreen(newStop ).toInt
                drawProc(start, x1, x2, adjustMove(start))
//
//              case Span.From(start) =>
//                val dStart    = adjustStart(start)
//                val newStart  = start + dStart
//                val x1        = frameToScreen(newStart).toInt
//                drawProc(start, x1, w + 5, adjustMove(start))
//
//              case Span.Until(stop) =>
//                val dStop     = adjustStop(stop)
//                val newStop   = stop + dStop
//                val x2        = frameToScreen(newStop).toInt
//                drawProc(Long.MinValue, -5, x2, 0L)
//
//              case Span.All =>
//                drawProc(Long.MinValue, -5, w + 5, 0L)
//
//              case _ => // don't draw Span.Void
//            }
         }

          // --- grapheme cursor and selection ---
          paintPosAndSelection(g, h)
        }
      }
    }
  }
}