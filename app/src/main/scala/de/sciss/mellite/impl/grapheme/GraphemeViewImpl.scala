/*
 *  GraphemeViewImpl.scala
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

package de.sciss.mellite.impl.grapheme

import java.awt.{Font, Graphics2D, RenderingHints}
import java.util.Locale

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.equal.Implicits._
import de.sciss.fingertree.OrderedSeq
import de.sciss.lucre.Txn.peer
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{BiPin, Disposable, Obj, Source}
import de.sciss.mellite.GraphemeView.Mode
import de.sciss.mellite.impl.TimelineViewBaseImpl
import de.sciss.mellite.{BasicTool, GUI, GraphemeTool, GraphemeTools, GraphemeView, Insets, ObjGraphemeView, ObjView, SelectionModel}
import de.sciss.model.Change
import de.sciss.numbers.Implicits._
import de.sciss.span.Span
import de.sciss.synth.UGenSource.Vec
import de.sciss.synth.proc.{Grapheme, TimeRef, Universe}
import javax.swing.{JComponent, UIManager}

import scala.annotation.tailrec
import scala.collection.immutable.{SortedMap => ISortedMap}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.{BorderPanel, BoxPanel, Component, Orientation}

object GraphemeViewImpl extends GraphemeView.Companion {
  def install(): Unit =
    GraphemeView.peer = this

  private val DEBUG   = false

  import de.sciss.mellite.Log.{timeline => logT}

//  private type EntryProc[T <: Txn[T]] = BiGroup.Entry[T, Proc[T]]

  def apply[T <: Txn[T]](gr: Grapheme[T])
                        (implicit tx: T, universe: Universe[T],
                         undo: UndoManager): GraphemeView[T] = {
    val sampleRate      = TimeRef.SampleRate
    val visStart        = 0L // obj.firstEvent.getOrElse(0L)
    val visStop         = gr.lastEvent.getOrElse((sampleRate * 60 * 2).toLong)
    val vis0            = Span(visStart, visStop)
    val bounds0         = Span(0L, (sampleRate * 60 * 60).toLong) // XXX TODO --- dynamically adjust
    val tlm             = TimelineModel(bounds = bounds0, visible = vis0, virtual = bounds0, clipStop = false,
      sampleRate = sampleRate)
    // tlm.visible         = Span(0L, (sampleRate * 60 * 2).toLong)
    val graphemeH       = tx.newHandle(gr)
    var disposables     = List.empty[Disposable[T]]
    val selectionModel  = SelectionModel[T, ObjGraphemeView[T]]
    val grView          = new Impl[T](graphemeH, tlm, selectionModel)

    // XXX TODO --- this is all horrible; we really need a proper iterator on grapheme
    // that gives time values and full leaf data
    gr.firstEvent.foreach { time0 =>
      @tailrec
      def populate(pred: List[ObjGraphemeView[T]], time: Long, entries: Vec[Grapheme.Entry[T]]): Unit = {
        val curr = entries.reverseIterator.map { entry =>
          val view = grView.objAddedInit(gr, time = time, entry = entry)
          view
        } .toList
        val succOpt = curr.headOption
        pred.foreach(_.succ_=(succOpt))
        val timeSuccOpt = gr.eventAfter(time)
        timeSuccOpt match {
          case Some(timeSucc) =>
            val entriesSucc = gr.intersect(timeSucc)
//            entriesSucc.foreach { e =>
//              assert (e.key.value == timeSucc)
//            }
            populate(curr, timeSucc, entriesSucc)
          case None =>
        }
      }

      val entries0 = gr.intersect(time0)
//      entries0.foreach { e =>
//        assert (e.key.value == time0)
//      }
      populate(Nil, time0, entries0)
    }

    val obsGrapheme = gr.changed.react { implicit tx => upd =>
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
          grView.objMoved(gr, entry, timeCh = timeChange)
      }
    }
    disposables ::= obsGrapheme

    grView.disposables.set(disposables)

    grView.init()
  }

  private final class Impl[T <: Txn[T]](val graphemeH     : Source[T, Grapheme[T]],
                                        val timelineModel : TimelineModel,
                                        val selectionModel: SelectionModel[T, ObjGraphemeView[T]])
                                       (implicit val universe: Universe[T],
                                        val undoManager: UndoManager)
    extends GraphemeView[T]
      with TimelineViewBaseImpl[T, Double, ObjGraphemeView[T]]
      with GraphemeActions[T]
      with ComponentHolder[Component] {

    impl =>

    type C = Component

    private type Child    = ObjGraphemeView[T]

    private final class ViewMapEntry(val key: Long, val value: List[Child]) {
      override def toString: String = s"ViewMapEntry($key, $value)"

      def copy(key: Long = this.key, value: List[Child] = this.value) =
        new ViewMapEntry(key, value)
    }

    private type ViewMap  = OrderedSeq[ViewMapEntry, Long]

    private def emptyMap: ViewMap = OrderedSeq.empty(_.key, Ordering.Long)

    // GUI-thread map of views; always reflects viewMapT
    private[this] var viewMapG: ViewMap = emptyMap
    // transactional map of views
    private[this] val viewMapT = Ref(viewMapG)
    // kind-of priority queue keeping track of horizontal margin needed when querying views to paint
    private[this] var viewMaxHorizG = ISortedMap.empty[Int, Int] // maxHoriz to count

    var canvas: GraphemeCanvasImpl[T] = _

    val disposables: Ref[List[Disposable[T]]] = Ref(Nil)

    def mode: Mode = Mode.TwoDim

    private[this] lazy val toolCursor   = GraphemeTool.cursor  [T](canvas)
    private[this] lazy val toolMove     = GraphemeTool.move    [T](canvas)
    private[this] lazy val toolAdd      = GraphemeTool.add     [T](canvas)

    def grapheme  (implicit tx: T): Grapheme[T] = graphemeH()
    def plainGroup(implicit tx: T): Grapheme[T] = grapheme

    def dispose()(implicit tx: T): Unit = {
      val m: ViewMap = emptyMap
      deferTx {
        viewMapG = m
      }
      disposables.swap(Nil).foreach(_.dispose())
      viewMapT.swap(m).iterator.foreach(_.value.foreach(_.dispose()))
    }

    def init()(implicit tx: T): this.type = {
      deferTx(guiInit())
      this
    }

    override protected def guiInit(): Unit = {
      super.guiInit()

      canvas = new View

      val transportPane = new BoxPanel(Orientation.Horizontal) {
        contents ++= Seq(
          HStrut(4),
          GraphemeTools.palette(canvas.graphemeTools, Vector(
            toolCursor, toolMove, toolAdd
          )),
          HStrut(4),
          ggChildAttr, ggChildView,
          HGlue
        )
      }

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

    private def addInsetsEDT(i: Insets): Unit = {
      val h = i.maxHorizontal
      val c = viewMaxHorizG.getOrElse(h, 0) + 1
      viewMaxHorizG += h -> c
    }

    private def removeInsetsEDT(i: Insets): Unit = {
      val h = i.maxHorizontal
      val c = viewMaxHorizG(h) - 1
      if (c === 0) viewMaxHorizG -= h else viewMaxHorizG += h -> c
    }

    def objAdded(gr: BiPin[T, Obj[T]], time: Long, entry: Grapheme.Entry[T])(implicit tx: T): Unit = {
      logT.debug(s"objAdded(time = $time / ${TimeRef.framesToSecs(time)}, entry.value = ${entry.value})")
      val a = addObjImpl(gr, time = time, entry = entry, updateSucc = true)
      deferTx {
        viewMapG = a.newViewMap
        addInsetsEDT(a.newView.insets)
        repaintAll()    // XXX TODO: optimize dirty rectangle
      }
    }

    def objAddedInit(gr: BiPin[T, Obj[T]], time: Long, entry: Grapheme.Entry[T])(implicit tx: T): Child = {
      logT.debug(s"objAddedInit(time = $time / ${TimeRef.framesToSecs(time)}, entry.value = ${entry.value})")
      assert (time == entry.key.value, s"time = $time, entry.key = ${entry.key.value}")
      val a = addObjImpl(gr, time = time, entry = entry, updateSucc = false)
      viewMapG = a.newViewMap
      addInsetsEDT(a.newView.insets)
      a.newView
    }

    private final class Added(val newView: Child, val newViewMap: ViewMap)

    // does not invoke EDT code
    private def addObjImpl(gr: BiPin[T, Obj[T]], time: Long, entry: Grapheme.Entry[T], updateSucc: Boolean)
                          (implicit tx: T): Added = {
      val view = ObjGraphemeView(entry = entry, mode = mode)
      val _viewMapG = viewMapT.transformAndGet { m =>
        val before  = m.get(time)
        before match {
          case Some(e)  =>
            m.removeAll(e) + e.copy(value = view :: e.value)
          case None =>
            m + new ViewMapEntry(key = time, value = view :: Nil)
        }
      }

      // XXX TODO -- do we need to remember the disposable?
      view.react { implicit tx => {
        case ObjView.Repaint(_) => objUpdated(view)
        case ObjGraphemeView.InsetsChanged(_, Change(before, now)) if before.maxHorizontal != now.maxHorizontal =>
          deferTx {
            removeInsetsEDT(before)
            addInsetsEDT   (now   )
            repaintAll()    // XXX TODO: optimize dirty rectangle
          }
        case _ =>
      }}

      if (updateSucc) {
        val succOpt = Some(view)
        gr.eventBefore(time).foreach { timePred =>
          viewMapT().get(timePred).foreach { viewsPred =>
            viewsPred.value.foreach { viewPred =>
              viewPred.succ_=(succOpt)
            }
          }
        }
        gr.eventAfter(time).foreach { timeSucc =>
          viewMapT().get(timeSucc).foreach { viewsSucc =>
            view.succ_=(viewsSucc.value.headOption)
          }
        }
      }

      new Added(view, _viewMapG)
    }

    private def warnViewNotFound(action: String, entry: Grapheme.Entry[T]): Unit =
      Console.err.println(s"Warning: Grapheme - $action. View for object $entry (value ${entry.value}) not found.")

    def objRemoved(gr: BiPin[T, Obj[T]], time: Long, entry: Grapheme.Entry[T])(implicit tx: T): Unit = {
      logT.debug(s"objRemoved($time, entry.value = ${entry.value})")
      val opt = removeObjImpl(gr = gr, time = time, entry = entry, isMove = false)
      opt.fold[Unit] {
        warnViewNotFound("remove", entry)
      } { r =>
        deferTx {
          viewMapG = r.newViewMap
          removeInsetsEDT(r.oldView.insets)
          selectionModel -= r.oldView
          repaintAll() // XXX TODO: optimize dirty rectangle
        }
      }
    }

    private final class Removed(val oldView: Child, val newViewMap: ViewMap)

    // does not invoke EDT code
    private def removeObjImpl(gr: BiPin[T, Obj[T]], time: Long, entry: Grapheme.Entry[T], isMove: Boolean)
                             (implicit tx: T): Option[Removed] = {
      val _viewMapG0 = viewMapT()
      val oldObj = entry.value
      for {
        views <- _viewMapG0.get(time)
        view  <- {
          val res = views.value.find(_.obj === oldObj)  // do _not_ directly compare the `entry`
          if (res.isEmpty) {
            if (DEBUG) println("OOPS - no view found despite time match")
          }
          res
        }
      } yield {
        val succOld     = views.value.headOption
        val viewsNew    = views.value.filterNot(_ === view)
        val succNew     = viewsNew.headOption

        if (succOld !== succNew) {
          gr.eventBefore(time).foreach { timePred =>
            _viewMapG0.get(timePred).fold {
              if (!isMove) warnViewNotFound("remove", entry)
            } { viewsPred =>
              viewsPred.value.foreach { viewPred =>
                viewPred.succ_=(succNew)
              }
            }
          }
        }

        val _viewMapG1 = _viewMapG0.removeAll(views)
        val _viewMapG2 = if (viewsNew.isEmpty) _viewMapG1 else {
          _viewMapG1 + views.copy(value = viewsNew)
        }
        viewMapT() = _viewMapG2

        view.dispose()
        new Removed(view, _viewMapG2)
      }
    }

    def objMoved(gr: BiPin[T, Obj[T]], entry: Grapheme.Entry[T], timeCh: Change[Long])
                (implicit tx: T): Unit = {
      logT.debug(s"objMoved(${timeCh.before} / ${TimeRef.framesToSecs(timeCh.before)} -> ${timeCh.now} / ${TimeRef.framesToSecs(timeCh.now)}, entry.value = ${entry.value})")
      val opt = removeObjImpl(gr = gr, time = timeCh.before, entry = entry, isMove = true)
      opt.fold[Unit] {
        warnViewNotFound("remove", entry)
      } { r =>
        val a = addObjImpl(gr, time = timeCh.now, entry = entry, updateSucc = true)
        deferTx {
          val wasSelected = selectionModel.contains(r.oldView)
          if (wasSelected) {
            selectionModel -= r.oldView
          }
          viewMapG = a.newViewMap
          val insetsChange = r.oldView.insets != a.newView.insets
          if (insetsChange) {
            removeInsetsEDT (r.oldView.insets)
            addInsetsEDT    (a.newView.insets)
          }
          if (wasSelected) {
            selectionModel += a.newView
          }
          repaintAll()    // XXX TODO: optimize dirty rectangle
        }
      }
    }

    private def objUpdated(view: ObjGraphemeView[T]): Unit = {
      repaintAll() // XXX TODO: optimize dirty rectangle
    }

    private final class View extends GraphemeCanvasImpl[T] {
      canvasImpl =>

      def timelineModel : TimelineModel                         = impl.timelineModel
      def selectionModel: SelectionModel[T, ObjGraphemeView[T]] = impl.selectionModel
      def grapheme(implicit tx: T): Grapheme[T]              = impl.plainGroup

//      def findChildView(frame: Long): Option[GraphemeObjView[T]] = {
//        val it = viewMapG.valuesIteratorFrom(frame)
//        if (it.hasNext) it.next().headOption else None
//      }

      def findChildViews(r: BasicTool.Rectangular[Double]): Iterator[ObjGraphemeView[T]] = {
        val dLeft   = math.ceil(screenToFrames(ObjGraphemeView.ScreenTolerance)).toLong
        val dRight  = dLeft // math.ceil(screenToFrames(GraphemeObjView.ScreenTolerance)).toLong
        val dTop    = screenToModelExtent     (ObjGraphemeView.ScreenTolerance)
        val dBottom = dTop // screenToModelExtent     (GraphemeObjView.ScreenTolerance)
        val frame1  = r.span.start  - dLeft
        val frame2  = r.span.stop   + dRight
        val modelY1 = r.modelYOffset - dTop
        val modelY2 = (r.modelYOffset + r.modelYExtent) + dBottom
        childIterator(frame1 = frame1, frame2 = frame2, modelY1 = modelY1, modelY2 = modelY2)
      }

      def iterator: Iterator[ObjGraphemeView[T]] = viewMapG.iterator.flatMap(_.value.headOption)

      def intersect(span: Span.NonVoid): Iterator[ObjGraphemeView[T]] = {
        ???
      }

      private def childIterator(frame1: Long, frame2: Long,
                                modelY1: Double, modelY2: Double): Iterator[ObjGraphemeView[T]] = {
        val it0 = viewMapG.iteratorFrom(frame1)
          .flatMap  (_.value.headOption)
          .dropWhile(_.timeValue <  frame1)
          .takeWhile(_.timeValue <= frame2)
        val it  = it0.filter {
          case hs: ObjGraphemeView.HasStartLevels[_] =>
            hs.startLevels.exists { value =>
              value >= modelY1 && value <= modelY2
            }
          case _ => true
        }
        it
      }

      def findChildView(frame: Long, modelY: Double): Option[ObjGraphemeView[T]] = {
        val dLeft   = math.ceil(screenToFrames(ObjGraphemeView.ScreenTolerance)).toLong
        val dRight  = dLeft // math.ceil(screenToFrames(GraphemeObjView.ScreenTolerance)).toLong
        val dTop    = screenToModelExtent     (ObjGraphemeView.ScreenTolerance)
        val dBottom = dTop // screenToModelExtent     (GraphemeObjView.ScreenTolerance)
        val frame1  = frame  - dLeft
        val frame2  = frame  + dRight
        val modelY1 = modelY - dTop
        val modelY2 = modelY + dBottom
        val it = childIterator(frame1 = frame1, frame2 = frame2, modelY1 = modelY1, modelY2 = modelY2)
        if (!it.hasNext) None else Some(it.minBy {
          case child: ObjGraphemeView.HasStartLevels[_] =>
            val dy = child.startLevels.iterator.map(value => modelExtentToScreen(value absDif modelY)).min
            val dx = framesToScreen(math.abs(child.timeValue - frame))  // XXX TODO --- Numbers should have absDif for Long
            dx.squared + dy.squared

          case child =>
            val dx = framesToScreen(math.abs(child.timeValue - frame))  // XXX TODO --- Numbers should have absDif for Long
            dx * dx
        })
      }

      protected def commitToolChanges(value: Any): Unit = {
        logT.debug(s"Commit tool changes $value")
        val editOpt = cursor.step { implicit tx =>
          value match {
//            case t: GraphemeTool.Cursor    => toolCursor .commit(t)
            case t: GraphemeTool.Move      => toolMove   .commit(t)
            case t: GraphemeTool.Add       => toolAdd    .commit(t)
            case _ => None
          }
        }
        editOpt.foreach(undoManager.add)
      }

      private[this] var _toolState    = Option.empty[Any]

      protected def toolState: Option[Any] = _toolState
      protected def toolState_=(state: Option[Any]): Unit = {
        _toolState    = state
        val r         = canvasComponent.rendering
        r.ttMoveState = GraphemeTool.NoMove

        state.foreach {
          case s: GraphemeTool.Move => r.ttMoveState  = s
          case _ =>
        }
      }

      object canvasComponent extends Component /* with DnD[T] */ /* with sonogram.PaintController */ {
        protected def graphemeModel: TimelineModel  = impl.timelineModel
//        protected def workspace: Workspace[T]       = impl.workspace

        // private var currentDrop = Option.empty[DnD.Drop[T]]

        font = {
          val f = UIManager.getFont("Slider.font", Locale.US)
          if (f != null) f.deriveFont(math.min(f.getSize2D, 9.5f)) else new Font("SansSerif", Font.PLAIN, 9)
        }

        preferredSize = {
          val b = desktop.Util.maximumWindowBounds
          (b.width >> 1, b.height >> 1)
        }

//        protected def updateDnD(drop: Option[DnD.Drop[T]]): Unit = {
//          currentDrop = drop
//          repaint()
//        }
//
//        protected def acceptDnD(drop: DnD.Drop[T]): Boolean = performDrop(drop)

        def imageObserver: JComponent = peer

        final val rendering: GraphemeRenderingImpl = new GraphemeRenderingImpl(this, GUI.isDarkSkin)

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
          def range() = viewMapG.floorIterator(visStartExt) // .takeWhile(_._1 < visStopExt)
  //          println(s"clipRect.x ${clipRect.x}, .x ${clipRect.width}, visStart $visStart, visStop $visStop, maxHorizF $maxHorizF, size = ${range.size}")
          var it: Iterator[ViewMapEntry] = range()
          var done  = false
          while (it.hasNext && !done) {
            val tup   = it.next()
            val view  = tup.value.head
            view.paintBack(g, impl, rendering)
            if (tup.key >= visStopExt) done = true
          }
          it    = range()
          done  = false
          while (it.hasNext && !done) {
            val tup   = it.next()
            val view  = tup.value.head
            view.paintFront(g, impl, rendering)
            if (tup.key >= visStopExt) done = true
          }

          // --- timeline cursor and selection ---
          paintPosAndSelection(g, h)

          // --- ongoing drag and drop / tools ---
          val _rubber = rubberState
          if (_rubber.isValid) {
            // println(s"drawDropFrame(modelYStart = ${_rubber.modelYOffset}, modelYStop = ${_rubber.modelYOffset + _rubber.modelYExtent}, span = ${_rubber.span}")
            drawDropFrame(g, modelYStart = _rubber.modelYOffset,
              modelYStop = _rubber.modelYOffset + _rubber.modelYExtent, span = _rubber.span, rubber = true)
          }
        }
      }
    }
  }
}