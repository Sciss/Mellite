/*
 *  GraphemeViewImpl.scala
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
package impl
package grapheme

import java.awt.{Font, Graphics2D, RenderingHints}
import java.util.Locale
import javax.swing.{JComponent, UIManager}

import de.sciss.audiowidgets.TimelineModel
import de.sciss.audiowidgets.impl.TimelineModelImpl
import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.icons.raphael
import de.sciss.lucre.bitemp.BiPin
import de.sciss.lucre.stm
import de.sciss.lucre.stm.TxnLike.peer
import de.sciss.lucre.stm.{Cursor, Disposable, Obj}
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeView.Mode
import de.sciss.model.Change
import de.sciss.span.Span
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.proc.{Grapheme, TimeRef, Workspace}

import scala.annotation.tailrec
import scala.collection.immutable.{SortedMap => ISortedMap}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.{Action, BorderPanel, BoxPanel, Component, Orientation}

object GraphemeViewImpl {
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
    val _grapheme       = obj
    val graphemeH       = tx.newHandle(obj)
    var disposables     = List.empty[Disposable[S#Tx]]
    val selectionModel  = SelectionModel[S, GraphemeObjView[S]]
    val grView          = new Impl[S](graphemeH, tlm, selectionModel)

    // XXX TODO --- this is all horrible; we really need a proper iterator on grapheme
    // that gives time values and full leaf data
    _grapheme.firstEvent.foreach { time0 =>
      @tailrec
      def populate(pred: List[GraphemeObjView[S]], time: Long, entries: Vec[Grapheme.Entry[S]]): Unit = {
        val curr = entries.reverseIterator.map { entry =>
          val view = grView.objAddedI(time, entry = entry, isInit = true)
          view
        } .toList
        val succOpt = curr.headOption
        pred.foreach(_.succ_=(succOpt))
        val timeSuccOpt = _grapheme.eventAfter(time)
        timeSuccOpt match {
          case Some(timeSucc) =>
            val entriesSucc = _grapheme.intersect(time)
            populate(curr, timeSucc, entriesSucc)
          case None =>
        }
      }

      val entries0 = _grapheme.intersect(time0)
      populate(Nil, time0, entries0)
    }

    val obsGrapheme = _grapheme.changed.react { implicit tx => upd =>
      val gr = upd.pin
      upd.changes.foreach {
        case BiPin.Added(time, entry) =>
          if (DEBUG) println(s"Added   $time, $entry")
          grView.objAdded(gr, time, entry)

        case BiPin.Removed(time, entry) =>
          if (DEBUG) println(s"Removed $time, $entry")
          grView.objRemoved(gr, time, entry)

        case BiPin.Moved  (timeChange, entry) =>
          if (DEBUG) println(s"Moved   $entry, $timeChange")
          grView.objMoved(entry, timeCh = timeChange)
      }
    }
    disposables ::= obsGrapheme

    grView.disposables.set(disposables)

    deferTx(grView.guiInit())
    grView
  }

  private final class Impl[S <: Sys[S]](val graphemeH     : stm.Source[S#Tx, Grapheme[S]],
                                        val timelineModel : TimelineModel,
                                        val selectionModel: SelectionModel[S, GraphemeObjView[S]])
                                       (implicit val workspace: Workspace[S], val cursor: Cursor[S],
                                        val undoManager: UndoManager)
    extends GraphemeActions[S]
      with GraphemeView[S]
      with ComponentHolder[Component] {

    impl =>

    type C = Component

    import cursor.step

    // GUI-thread map of views; always reflects viewMapT
    private[this] var viewMapG      = ISortedMap.empty[Long, List[GraphemeObjView[S]]]
    // transactional map of views
    private[this] val viewMapT      = Ref(viewMapG)
    // kind-of priority queue keeping track of horizontal margin needed when querying views to paint
    private[this] var viewMaxHorizG = ISortedMap.empty[Int, Int] // maxHoriz to count

    var canvas: GraphemeCanvasImpl[S] = _

    val disposables           = Ref(List.empty[Disposable[S#Tx]])

    def mode: Mode = Mode.TwoDim

//    private lazy val toolCursor   = TrackTool.cursor  [S](canvasView)
//    private lazy val toolMove     = TrackTool.move    [S](canvasView)

    def grapheme  (implicit tx: S#Tx): Grapheme[S] = graphemeH()
    def plainGroup(implicit tx: S#Tx): Grapheme[S] = grapheme

//    def window: Window = component.peer.getClientProperty("de.sciss.mellite.Window").asInstanceOf[Window]

//    def canvasComponent: Component = canvasView.canvasComponent

    def dispose()(implicit tx: S#Tx): Unit = {
      val empty = ISortedMap.empty[Long, List[GraphemeObjView[S]]]
      deferTx {
        viewMapG = empty
      }
      disposables.swap(Nil).foreach(_.dispose())
      viewMapT.swap(empty).foreach(_._2.foreach(_.dispose()))
    }

    def guiInit(): Unit = {
      canvas = new View

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

      val pane2 = canvas.component
//      pane2.dividerSize         = 4
//      pane2.border              = null
//      pane2.oneTouchExpandable  = true

      val boxWest = new BoxPanel(Orientation.Vertical) {
        contents += VStrut(16) // XXX TODO --- incorrect: transportPane.preferredSize.height)
        contents += canvas.yAxis
        contents += VStrut(16) // XXX TODO --- TimelineCanvasImpl should offer `scroll` access
      }

      val pane = new BorderPanel {
        import BorderPanel.Position._
        layoutManager.setVgap(2)
        add(transportPane, North )
        add(pane2        , Center)
        // add(ggTrackPos   , East  )
        add(boxWest      , West  )
      }

      component = pane
      // DocumentViewHandler.instance.add(this)
    }

    private def repaintAll(): Unit = canvas.canvasComponent.repaint()

    private def addInsetsG(i: Insets): Unit = {
      val h = i.maxHoriz
      val c = viewMaxHorizG.getOrElse(h, 0) + 1
      viewMaxHorizG += h -> c
    }

    private def removeInsetsG(i: Insets): Unit = {
      val h = i.maxHoriz
      val c = viewMaxHorizG(h) - 1
      if (c == 0) viewMaxHorizG -= h else viewMaxHorizG += h -> c
    }

    def objAdded(gr: BiPin[S, Obj[S]], time: Long, entry: Grapheme.Entry[S])(implicit tx: S#Tx): Unit = {
      val view = objAddedI(time = time, entry = entry, isInit = false)
      val succOpt = Some(view)
      gr.eventBefore(time).foreach { timePred =>
        viewMapT().get(timePred).foreach { viewsPred =>
//          deferTx {
            viewsPred.foreach { viewPred =>
              viewPred.succ_=(succOpt)
            }
//          }
        }
      }
      gr.eventAfter(time).foreach { timeSucc =>
        viewMapT().get(timeSucc).foreach { viewsSucc =>
//          deferTx {
            view.succ_=(viewsSucc.headOption)
//          }
        }
      }
      deferTx(repaintAll())    // XXX TODO: optimize dirty rectangle
    }

    def objAddedI(time: Long, entry: Grapheme.Entry[S], isInit: Boolean)
                 (implicit tx: S#Tx): GraphemeObjView[S] = {
      logT(s"objAdded(time = $time / ${TimeRef.framesToSecs(time)}, entry = $entry / tpe: ${entry.value.tpe})")
      // entry.span
      // val proc = entry.value

      // val pv = ProcView(entry, viewMap, scanMap)
      val view = GraphemeObjView(entry = entry, mode = mode)
      val _viewMapG = viewMapT.transformAndGet(m => m + (time -> (view :: m.getOrElse(time, Nil))))

      def doAdd(): Unit = {
        viewMapG = _viewMapG
        addInsetsG(view.insets)
      }

      if (isInit)
        doAdd()
      else
        deferTx(doAdd())

      // XXX TODO -- do we need to remember the disposable?
      view.react { implicit tx => {
        case ObjView.Repaint(_) => objUpdated(view)
        case GraphemeObjView.InsetsChanged(_, Change(before, now)) if before.maxHoriz != now.maxHoriz =>
          deferTx {
            removeInsetsG(before)
            addInsetsG   (now   )
            repaintAll()    // XXX TODO: optimize dirty rectangle
          }
        case _ =>
      }}

      view
    }

    private def warnViewNotFound(action: String, entry: Grapheme.Entry[S]): Unit =
      Console.err.println(s"Warning: Grapheme - $action. View for object $entry not found.")

    def objRemoved(gr: BiPin[S, Obj[S]], time: Long, entry: Grapheme.Entry[S])(implicit tx: S#Tx): Unit = {
      logT(s"objRemoved($time, $entry)")
      val _viewMapG0 = viewMapT()
      _viewMapG0.get(time).fold {
        warnViewNotFound("remove", entry)
      } { views =>
        views.find(_.entry == entry).fold {
          warnViewNotFound("remove", entry)
        } { view =>
          val succOld     = views.headOption
          val viewsNew    = views.filterNot(_ == view)
          val succNew     = viewsNew.headOption

          if (succOld != succNew) {
            gr.eventBefore(time).foreach { timePred =>
              _viewMapG0.get(timePred).fold {
                warnViewNotFound("remove", entry)
              } { viewsPred =>
//                deferTx {
                  viewsPred.foreach { viewPred =>
                    viewPred.succ_=(succNew)
                  }
//                }
              }
            }
          }

          val _viewMapG1  = if (viewsNew.isEmpty) {
            _viewMapG0 + (time -> viewsNew)
          } else {
            _viewMapG0 - time
          }
          viewMapT() = _viewMapG1

          val insets = view.insets
          view.dispose()
          deferTx {
            viewMapG = _viewMapG1
            removeInsetsG(insets)
            repaintAll() // XXX TODO: optimize dirty rectangle
          }
        }
      }
    }

    // insignificant changes are ignored, therefore one can just move the span without the track
    // by using trackCh = Change(0,0), and vice versa
    def objMoved(entry: Grapheme.Entry[S], timeCh: Change[Long])
                (implicit tx: S#Tx): Unit = {
      logT(s"objMoved(${timeCh.before} / ${TimeRef.framesToSecs(timeCh.before)} -> ${timeCh.now} / ${TimeRef.framesToSecs(timeCh.now)}, $entry)")
      ???!
//      viewMap.remove(timeCh.before).fold {
//        warnViewNotFound("move", entry)
//      } { view =>
//        viewMap.put(timeCh.now, view)
//        deferTx {
//          viewRange -= timeCh.before
//          view.timeValue = timeCh .now
//          viewRange += timeCh.now -> view
//          repaintAll()  // XXX TODO: optimize dirty rectangle
//        }
//      }
    }

    private def objUpdated(view: GraphemeObjView[S]): Unit = {
      repaintAll() // XXX TODO: optimize dirty rectangle
    }

    private final class View extends GraphemeCanvasImpl[S] {
      canvasImpl =>

      // import AbstractGraphemeView._
      def timelineModel : TimelineModel                         = impl.timelineModel
      def selectionModel: SelectionModel[S, GraphemeObjView[S]] = impl.selectionModel
      def grapheme(implicit tx: S#Tx): Grapheme[S]              = impl.plainGroup

//      def findView(pos: Long): Option[GraphemeObjView[S]] =
//        viewMapG.range(pos, Long.MaxValue).headOption.flatMap(_._2.headOption)

      def findView(pos: Long): Option[GraphemeObjView[S]] = {
        val it = viewMapG.valuesIteratorFrom(pos)
        if (it.hasNext) it.next().headOption else None
      }

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

      private[this] var _toolState    = Option.empty[Any]
      private[this] var moveState     = NoMove

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

        final val rendering: GraphemeRendering = new GraphemeRenderingImpl(this, Mellite.isDarkSkin)

        override protected def paintComponent(g: Graphics2D): Unit = {
          super.paintComponent(g)
          val w = peer.getWidth
          val h = peer.getHeight
          g.setPaint(rendering.pntBackground)
          g.fillRect(0, 0, w, h)

          import rendering.clipRect
          g.getClipBounds(clipRect)

          val visStart  = screenToFrame(clipRect.x).toLong
          val visStop   = screenToFrame(clipRect.x + clipRect.width).toLong + 1 // plus one to avoid glitches

          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON)
          g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE )

          val maxHorizF = if (viewMaxHorizG.isEmpty) 0L else {
            val x = viewMaxHorizG.lastKey
//            println(s"viewMaxHoriz = $x")
            screenToFrames(x).toLong
          }

          // warning: if we use iterator, beware that we need to traverse twice!
          val visStartExt = visStart - maxHorizF
          val visStopExt  = visStop  + maxHorizF
          // val range = viewMapG.range(visStartExt, visStopExt)
          def range = viewMapG.iteratorFrom(visStartExt) // .takeWhile(_._1 < visStopExt)
  //          println(s"clipRect.x ${clipRect.x}, .x ${clipRect.width}, visStart $visStart, visStop $visStop, maxHorizF $maxHorizF, size = ${range.size}")
          var it    = range
          var done  = false
          while (it.hasNext && !done) {
            val tup   = it.next()
            val view  = tup._2.head
            view.paintBack(g, impl, rendering)
            if (tup._1 >= visStopExt) done = true
          }
          it    = range
          done  = false
          while (it.hasNext && !done) {
            val tup   = it.next()
            val view  = tup._2.head
            view.paintFront(g, impl, rendering)
            if (tup._1 >= visStopExt) done = true
          }

          // --- timeline cursor and selection ---
          paintPosAndSelection(g, h)
        }
      }
    }
  }
}