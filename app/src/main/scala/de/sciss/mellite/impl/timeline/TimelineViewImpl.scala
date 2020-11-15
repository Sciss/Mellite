/*
 *  TimelineViewImpl.scala
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

package de.sciss.mellite.impl.timeline

import java.awt.datatransfer.Transferable
import java.awt.{Font, Graphics2D, RenderingHints}
import java.util.Locale

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.equal.Implicits._
import de.sciss.fingertree.RangedSeq
import de.sciss.lucre.impl.BiGroupImpl
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{BiGroup, IdentMap, IntObj, Obj, Source, SpanLikeObj}
import de.sciss.mellite.edit.{EditFolderInsertObj, EditTimelineInsertObj, Edits}
import de.sciss.mellite.impl.component.DragSourceButton
import de.sciss.mellite.impl.objview.{CodeObjView, IntObjView, TimelineObjView}
import de.sciss.mellite.impl.proc.ProcObjView
import de.sciss.mellite.impl.{TimelineCanvas2DImpl, TimelineViewBaseImpl}
import de.sciss.mellite.{ActionArtifactLocation, AudioCueObjView, BasicTool, DragAndDrop, GUI, GlobalProcsView, Mellite, ObjTimelineView, ObjView, ObjectActions, ProcActions, SelectionModel, TimelineTool, TimelineTools, TimelineView}
import de.sciss.model.Change
import de.sciss.span.{Span, SpanLike}
import de.sciss.swingplus.ScrollBar
import de.sciss.audiofile.AudioFile
import de.sciss.synth.proc.gui.TransportView
import de.sciss.synth.proc.impl.AuxContextImpl
import de.sciss.synth.proc.{AudioCue, TimeRef, Timeline, Transport, Universe}
import javax.swing.UIManager
import javax.swing.undo.UndoableEdit

import scala.concurrent.stm.TSet
import scala.math.{max, min}
import scala.swing.Swing._
import scala.swing.event.ValueChanged
import scala.swing.{BorderPanel, BoxPanel, Component, Orientation, SplitPane}
import scala.util.Try

object TimelineViewImpl extends TimelineView.Companion {
  def install(): Unit =
    TimelineView.peer = this

  private final val LinkArrowLen  = 0   // 10  ; currently no arrow tip painted
  private final val LinkCtrlPtLen = 20

  private val DEBUG = false // true

  import de.sciss.mellite.Mellite.{logTimeline => logT}

  def apply[T <: Txn[T]](obj: Timeline[T])
                        (implicit tx: T, universe: Universe[T],
                         undo: UndoManager): TimelineView[T] = {
    val sampleRate  = TimeRef.SampleRate
    val visStart    = 0L // obj.firstEvent.getOrElse(0L)
    val lastOpt     = obj.lastEvent
    val visStop     = lastOpt.fold((sampleRate * 60 * 2).toLong)(f => f + f/2)
    val vis0        = Span(visStart, visStop)
    val vir0        = vis0
    val lastOrZero  = lastOpt.getOrElse(0L)
    val bounds0     = Span(min(0L, lastOrZero), lastOrZero)
    val tlm         = TimelineModel(bounds = bounds0, visible = vis0, virtual = vir0, clipStop = false,
      sampleRate = sampleRate)
    // tlm.visible     = Span(0L, (sampleRate * 60 * 2).toLong)
    val timeline    = obj
    val timelineH   = tx.newHandle(obj)

    // XXX TODO --- should use TransportView now!

    // ugly: the view dispose method cannot iterate over the proc objects
    // (other than through a GUI driven data structure). thus, it
    // only call pv.disposeGUI() and the procMap and scanMap must be
    // freed directly...
    val viewMap = tx.newIdentMap[ObjTimelineView[T]]

    val transport = Transport[T](universe) // = proc.Transport [T, workspace.I](group, sampleRate = sampleRate)
    transport.addObject(obj)

    // val globalSelectionModel = SelectionModel[T, ProcView[T]]
    val selectionModel = SelectionModel[T, ObjTimelineView[T]]
    val global = GlobalProcsView(timeline, selectionModel)

    import universe.cursor
    val transportView = TransportView(transport, tlm, hasMillis = true, hasLoop = true)
    val tlView = new Impl[T](timelineH, viewMap, /* scanMap, */ tlm, selectionModel, global, transportView, tx)

    tlView.init(obj)
  }

  private final class Impl[T <: Txn[T]](val objH: Source[T, Timeline[T]],
                                        val viewMap: ObjTimelineView.Map[T],
                                        //                                        val scanMap: ProcObjView.ScanMap[T],
                                        val timelineModel: TimelineModel.Modifiable,
                                        val selectionModel: SelectionModel[T, ObjTimelineView[T]],
                                        val globalView: GlobalProcsView[T],
                                        val transportView: TransportView[T], tx0: T)
    extends TimelineView[T] with TimelineObjView.Basic[T]
      with TimelineViewBaseImpl[T, Int, ObjTimelineView[T]]
      with TimelineActions[T]
      with ComponentHolder[Component]
      with ObjTimelineView.Context[T]
      with AuxContextImpl[T] {

    impl =>


    type C = Component

    override type Repr = Timeline[T]

    implicit val universe: Universe[T] = globalView.universe

    def undoManager: UndoManager = globalView.undoManager

    private def transport: Transport[T] = transportView.transport

    private[this] var viewRange = RangedSeq.empty[ObjTimelineView[T], Long]
    private[this] val viewSet   = TSet     .empty[ObjTimelineView[T]]

    var canvas      : TimelineTrackCanvasImpl[T]  = _

    protected val auxMap      : IdentMap[T, Any]                = tx0.newIdentMap
    protected val auxObservers: IdentMap[T, List[AuxObserver]]  = tx0.newIdentMap

    private[this] lazy val toolCursor   = TimelineTool.cursor  [T](canvas)
    private[this] lazy val toolMove     = TimelineTool.move    [T](canvas)
    private[this] lazy val toolResize   = TimelineTool.resize  [T](canvas)
    private[this] lazy val toolGain     = TimelineTool.gain    [T](canvas)
    private[this] lazy val toolMute     = TimelineTool.mute    [T](canvas)
    private[this] lazy val toolFade     = TimelineTool.fade    [T](canvas)
    private[this] lazy val toolFunction = TimelineTool.function[T](canvas, this)
    private[this] lazy val toolPatch    = TimelineTool.patch   [T](canvas)
    private[this] lazy val toolAudition = TimelineTool.audition[T](canvas, this)

    def plainGroup(implicit tx: T): Timeline[T] = obj

    override def dispose()(implicit tx: T): Unit = {
      super.dispose()
      transport .dispose()
      viewMap   .dispose()
      globalView.dispose()

      deferTx {
        viewRange = RangedSeq.empty
      }
      viewSet.foreach(_.dispose())(tx.peer)
      clearSet(viewSet)
    }

    private def clearSet[A](s: TSet[A])(implicit tx: T): Unit =
      s.retain(_ => false)(tx.peer) // no `clear` method

    private def debugCheckConsistency(info: => String)(implicit tx: T): Unit = if (DEBUG) {
      val check = BiGroupImpl.verifyConsistency(plainGroup, reportOnly = true)
      check.foreach { msg =>
        println(info)
        println(msg)
        sys.error("Rollback")
      }
    }

    def init(timeline: Timeline[T])(implicit tx: T): this.type = {
      initAttrs(timeline)

      addDisposable(timeline.changed.react { implicit tx => upd =>
        upd.changes.foreach {
          case BiGroup.Added(span, timed) =>
            if (DEBUG) println(s"Added   $span, $timed")
            objAdded(span, timed, repaint = true)

          case BiGroup.Removed(span, timed) =>
            if (DEBUG) println(s"Removed $span, $timed")
            objRemoved(span, timed)

          case BiGroup.Moved(spanChange, timed) =>
            if (DEBUG) println(s"Moved   $timed, $spanChange")
            objMoved(timed, spanCh = spanChange, trackCh = None)
        }
      })

      deferTx(guiInit())

      // must come after guiInit because views might call `repaint` in the meantime!
      timeline.iterator.foreach { case (span, seq) =>
        seq.foreach { timed =>
          objAdded(span, timed, repaint = false)
        }
      }

      this
    }

    override def createTransferable(): Option[Transferable] = Some(mkDefaultTransferable())

    private def mkDefaultTransferable(): Transferable =
      DragAndDrop.Transferable(TimelineView.Flavor)(TimelineView.Drag(universe, impl))

    override protected def guiInit(): Unit = {
      super.guiInit()

      canvas = new View
      val ggVisualBoost = GUI.boostRotary()(canvas.timelineTools.visualBoost = _)

      val ggDragObject = new DragSourceButton() {
        protected def createTransferable(): Option[Transferable] = {
          val t1  = mkDefaultTransferable()
          val t2  = DragAndDrop.Transferable(ObjView.Flavor)(ObjView.Drag(universe, impl))
          val t   = DragAndDrop.Transferable.seq(t1, t2)
          Some(t)
        }
        tooltip = "Drag Timeline Object or Selection"
      }

      val topPane = new BoxPanel(Orientation.Horizontal) {
        contents ++= Seq(
          ggDragObject,
          HStrut(4),
          TimelineTools.palette(canvas.timelineTools, Vector(
            toolCursor, toolMove, toolResize, toolGain, toolFade /* , toolSlide*/ ,
            toolMute, toolAudition, toolFunction, toolPatch
          )),
          HStrut(4),
          ggChildAttr, ggChildView,
          HStrut(8),
          ggVisualBoost,
          HGlue,
          HStrut(4),
          transportView.component,
          HStrut(4)
        )
      }

      val ggTrackPos = new ScrollBar
      ggTrackPos.maximum = 512 // 128 tracks at default "full-size" (4)
      ggTrackPos.listenTo(ggTrackPos)
      ggTrackPos.reactions += {
        case ValueChanged(_) => canvas.trackIndexOffset = ggTrackPos.value
      }

      var hadSelectedObjects = false

      selectionModel.addListener {
        case _ =>
          val hasSome = selectionModel.nonEmpty
          if (hadSelectedObjects !== hasSome) {
            hadSelectedObjects = hasSome
            actionSplitObjects        .enabled = hasSome
            actionCleanUpObjects      .enabled = hasSome
            actionAlignObjectsToCursor.enabled = hasSome
          }
      }

      var hasSelectedSpan = false

      timelineModel.addListener {
        case TimelineModel.Selection(_, span) if span.before.isEmpty !== span.now.isEmpty =>
          val hasSome = span.now.nonEmpty
          if (hasSelectedSpan !== hasSome) {
            hasSelectedSpan = hasSome
            actionClearSpan .enabled = hasSome
            actionRemoveSpan.enabled = hasSome
          }
      }

      val pane2 = new SplitPane(Orientation.Vertical, globalView.component, canvas.component)
      pane2.dividerSize         = 4
      pane2.border              = null
      pane2.oneTouchExpandable  = true

      val pane = new BorderPanel {

        import BorderPanel.Position._

        layoutManager.setVgap(2)
        add(topPane, North)
        add(pane2, Center)
        add(ggTrackPos, East)
        // add(globalView.component, West  )
      }

      component = pane
      // DocumentViewHandler.instance.add(this)
    }

    private def repaintAll(): Unit = canvas.canvasComponent.repaint()

    private def objAdded(span: SpanLike, timed: BiGroup.Entry[T, Obj[T]], repaint: Boolean)(implicit tx: T): Unit = {
      logT(s"objAdded($span / ${TimeRef.spanToSecs(span)}, $timed)")
      // timed.span
      // val proc = timed.value

      // val pv = ProcView(timed, viewMap, scanMap)
      val view = ObjTimelineView(timed, this)
      viewMap.put(timed.id, view)
      viewSet.add(view)(tx.peer)

      def doAdd(): Unit = {
        view match {
          case pv: ProcObjView.Timeline[T] if pv.isGlobal =>
            globalView.add(pv)
          case _ =>
            viewRange += view
            if (repaint) {
              val oldBounds = timelineModel.bounds
              val newBounds = span match {
                case sp: Span             => oldBounds union sp
                case Span.From(start)     => oldBounds match {
                  case sp: Span           => Span(min(start, sp.start), sp.stop)
                  case Span.From(start1)  => Span.From(min(start, start1))
                  case _                  => oldBounds
                }
                case Span.Until(stop)     => oldBounds match {
                  case sp: Span           => Span(sp.start, max(stop, sp.stop))
                  case Span.Until(stop1)  => Span.Until(max(stop, stop1))
                  case _                  => oldBounds
                }
                case _ => oldBounds
              }
              if (newBounds !== oldBounds) {
                timelineModel.setBoundsExtendVirtual(newBounds)
              }
              repaintAll()  // XXX TODO: optimize dirty rectangle
            }
        }
      }

      if (repaint)
        deferTx(doAdd())
      else
        doAdd()

      // XXX TODO -- do we need to remember the disposable?
      view.react { implicit tx => {
        case ObjView.Repaint(_) => objUpdated(view)
        case _ =>
      }}
    }

    private def warnViewNotFound(action: String, timed: BiGroup.Entry[T, Obj[T]]): Unit =
      Console.err.println(s"Warning: Timeline - $action. View for object $timed not found.")

    private def objRemoved(span: SpanLike, timed: BiGroup.Entry[T, Obj[T]])(implicit tx: T): Unit = {
      logT(s"objRemoved($span, $timed)")
      val id = timed.id
      viewMap.get(id).fold {
        warnViewNotFound("remove", timed)
      } { view =>
        viewMap.remove(id)
        viewSet.remove(view)(tx.peer)
        deferTx {
          selectionModel -= view
          view match {
            case pv: ProcObjView.Timeline[T] if pv.isGlobal => globalView.remove(pv)
            case _ =>
              viewRange -= view
              repaintAll() // XXX TODO: optimize dirty rectangle
          }
        }
        view.dispose()
      }
    }

    // insignificant changes are ignored, therefore one can just move the span without the track
    // by using trackCh = Change(0,0), and vice versa
    private def objMoved(timed: BiGroup.Entry[T, Obj[T]], spanCh: Change[SpanLike], trackCh: Option[(Int, Int)])
                (implicit tx: T): Unit = {
      logT(s"objMoved(${spanCh.before} / ${TimeRef.spanToSecs(spanCh.before)} -> ${spanCh.now} / ${TimeRef.spanToSecs(spanCh.now)}, $timed)")
      viewMap.get(timed.id).fold {
        warnViewNotFound("move", timed)
      } { view =>
        deferTx {
          view match {
            case pv: ProcObjView.Timeline[T] if pv.isGlobal => globalView.remove(pv)
            case _ => viewRange -= view
          }

          if (spanCh.isSignificant) view.spanValue = spanCh.now
          trackCh.foreach { case (idx, h) =>
            view.trackIndex = idx
            view.trackHeight = h
          }

          view match {
            case pv: ProcObjView.Timeline[T] if pv.isGlobal => globalView.add(pv)
            case _ =>
              viewRange += view
              repaintAll() // XXX TODO: optimize dirty rectangle
          }
        }
      }
    }

    private def objUpdated(view: ObjTimelineView[T])(implicit tx: T): Unit = deferTx {
      //      if (view.isGlobal)
      //        globalView.updated(view)
      //      else
      repaintAll() // XXX TODO: optimize dirty rectangle
    }

    // TODO - this could be defined by the view?
    // call on EDT!
    private def defaultDropLength(view: ObjView[T], inProgress: Boolean): Long = {
      val d = view match {
        case a: AudioCueObjView[T] =>
          val v = a.value
          (v.numFrames * TimeRef.SampleRate / v.sampleRate).toLong
        case _: ProcObjView[T] =>
          timelineModel.sampleRate * 2 // two seconds
        case _ =>
          if (inProgress)
            canvas.screenToFrame(4) // four pixels
          else
            timelineModel.sampleRate * 1 // one second
      }
      val res = d.toLong
      // println(s"defaultDropLength(inProgress = $inProgress) -> $res"  )
      res
    }

    private def insertAudioRegion(drop: DnD.Drop[T], drag: DnD.AudioDragLike[T],
                                  audioCue: AudioCue.Obj[T])(implicit tx: T): Option[UndoableEdit] =
      plainGroup.modifiableOption.map { groupM =>
        logT(s"insertAudioRegion($drop, ${drag.selection}, $audioCue)")
        val tlSpan = Span(drop.frame, drop.frame + drag.selection.length)
        val (span, obj) = ProcActions.mkAudioRegion(time = tlSpan,
          audioCue = audioCue, gOffset = drag.selection.start /*, bus = None */) // , bus = ad.bus.map(_.apply().entity))
        val track = canvas.screenToModelPos(drop.y)
        obj.attr.put(ObjTimelineView.attrTrackIndex, IntObj.newVar(IntObj.newConst(track)))
        val edit = EditTimelineInsertObj("Insert Audio Region", groupM, span, obj)
        edit
      }

    private def performDrop(drop: DnD.Drop[T]): Boolean = {
      def withRegions[A](fun: T => List[ObjTimelineView[T]] => Option[A]): Option[A] =
        canvas.findChildView(drop.frame, canvas.screenToModelPos(drop.y)).flatMap { hitRegion =>
          val regions = if (selectionModel.contains(hitRegion)) selectionModel.iterator.toList else hitRegion :: Nil
          cursor.step { implicit tx =>
            fun(tx)(regions)
          }
        }

      def withProcRegions[A](fun: T => List[ProcObjView[T]] => Option[A]): Option[A] =
        canvas.findChildView(drop.frame, canvas.screenToModelPos(drop.y)).flatMap {
          case hitRegion: ProcObjView[T] =>
            val regions = if (selectionModel.contains(hitRegion)) {
              selectionModel.iterator.collect {
                case pv: ProcObjView[T] => pv
              }.toList
            } else hitRegion :: Nil

            cursor.step { implicit tx =>
              fun(tx)(regions)
            }
          case _ => None
        }

      // println(s"performDrop($drop)")

      val editOpt: Option[UndoableEdit] = drop.drag match {
        case ad: DnD.AudioDrag[T] =>
          cursor.step { implicit tx =>
            insertAudioRegion(drop, ad, ad.source())
          }

        case ed: DnD.ExtAudioRegionDrag[T] =>
          val file = ed.file
          val resOpt = cursor.step { implicit tx =>
            val ex = ObjectActions.findAudioFile(universe.workspace.root, file)
            ex.flatMap { grapheme =>
              insertAudioRegion(drop, ed, grapheme)
            }
          }

          resOpt.orElse[UndoableEdit] {
            val tr = Try(AudioFile.readSpec(file)).toOption
            tr.flatMap { spec =>
              ActionArtifactLocation.query[T](file.toURI)(implicit tx => universe.workspace.root).flatMap { either =>
                cursor.step { implicit tx =>
                  ActionArtifactLocation.merge(either).flatMap { case (list0, locM) =>
                    val folder = universe.workspace.root
                    // val obj   = ObjectActions.addAudioFile(elems, elems.size, loc, file, spec)
                    val obj = ObjectActions.mkAudioFile(locM, file.toURI, spec)
                    val edits0 = list0.map(obj => EditFolderInsertObj("Location", folder, folder.size, obj)).toList
                    val edits1 = edits0 :+ EditFolderInsertObj("Audio File", folder, folder.size, obj)
                    val edits2 = insertAudioRegion(drop, ed, obj).fold(edits1)(edits1 :+ _)
                    CompoundEdit(edits2, "Insert Audio Region")
                  }
                }
              }
            }
          }

        case DnD.ObjectDrag(_, view: IntObjView[T]) => withRegions { implicit tx => regions =>
          val intExpr = view.obj
          Edits.setBus[T](regions.map(_.obj), intExpr)
        }

        case DnD.ObjectDrag(_, view: CodeObjView[T]) => withProcRegions { implicit tx => regions =>
          val codeElem = view.obj
          import Mellite.compiler
          Edits.setSynthGraph[T](regions.map(_.obj), codeElem)
        }

        case DnD.ObjectDrag(ws, view: AudioCueObjView[T]) =>
          val length  = defaultDropLength(view, inProgress = false)
          val span    = Span(0L, length)
          val ad      = DnD.AudioDrag(ws, view.objH, span)
          cursor.step { implicit tx =>
            insertAudioRegion(drop, ad, ad.source())
          }

        // quick fix to forbid that we "drop ourselves onto ourselves"
        // ; must be stated before the next match case!
        case DnD.ObjectDrag(_, `impl`) => None

        case DnD.ObjectDrag(_, view /* : ObjView.Proc[T] */) => cursor.step { implicit tx =>
          plainGroup.modifiableOption.map { group =>
            val length  = defaultDropLength(view, inProgress = false)
            val span    = Span(drop.frame, drop.frame + length)
            val spanEx  = SpanLikeObj.newVar[T](SpanLikeObj.newConst(span))
            EditTimelineInsertObj(s"Insert ${view.humanName}", group, spanEx, view.obj)
          }
          // CompoundEdit(edits, "Insert Objects")
        }

        case pd: DnD.GlobalProcDrag[T] => withProcRegions { implicit tx => regions =>
          val in = pd.source()
          val edits = regions.flatMap { pv =>
            val out = pv.obj
            Edits.linkOrUnlink[T](out, in)
          }
          CompoundEdit(edits, "Link Global Proc")
        }

        case _ => None
      }

      editOpt.foreach(undoManager.add)
      editOpt.isDefined
    }

    private final class View extends TimelineTrackCanvasImpl[T] {
      canvasImpl =>

      def timelineModel : TimelineModel                         = impl.timelineModel
      def selectionModel: SelectionModel[T, ObjTimelineView[T]] = impl.selectionModel

      def timeline(implicit tx: T): Timeline[T] = impl.plainGroup

      def iterator: Iterator[ObjTimelineView[T]] = viewRange.iterator

      def intersect(span: Span.NonVoid): Iterator[ObjTimelineView[T]] = {
        val start = span match {
          case hs: Span.HasStart => hs.start
          case _ => Long.MinValue
        }
        val stop  = span match {
          case hs: Span.HasStop  => hs.stop
          case _ => Long.MaxValue
        }
        viewRange.filterOverlaps((start, stop))
      }

      def findChildView(pos: Long, modelY: Int): Option[ObjTimelineView[T]] = {
        val span      = Span(pos, pos + 1)
        val children  = intersect(span)
        children.find(pv => pv.trackIndex <= modelY && (pv.trackIndex + pv.trackHeight) > modelY)
      }

      def findChildViews(r: BasicTool.Rectangular[Int]): Iterator[ObjTimelineView[T]] = {
        val children = intersect(r.span)
        children.filter(pv => pv.trackIndex < r.modelYOffset + r.modelYExtent && (pv.trackIndex + pv.trackHeight) > r.modelYOffset)
      }

      protected def commitToolChanges(value: Any): Unit = {
        logT(s"Commit tool changes $value")
        val editOpt = cursor.step { implicit tx =>
          value match {
            case t: TimelineTool.Cursor => toolCursor commit t
            case t: TimelineTool.Move =>
              // println("\n----BEFORE----")
              // println(group.debugPrint)
              val res = toolMove.commit(t)
              // println("\n----AFTER----")
              // println(group.debugPrint)
              debugCheckConsistency(s"Move $t")
              res

            case t: TimelineTool.Resize =>
              val res = toolResize commit t
              debugCheckConsistency(s"Resize $t")
              res

            case t: TimelineTool.Gain => toolGain commit t
            case t: TimelineTool.Mute => toolMute commit t
            case t: TimelineTool.Fade => toolFade commit t
            case t: TimelineTool.Add => toolFunction commit t
            case t: TimelineTool.Patch[T] => toolPatch commit t
            case _ => None
          }
        }
        editOpt.foreach(undoManager.add)
      }

      private[this] val NoPatch = TimelineTool.Patch[T](null, null)
      // not cool
      private[this] var _toolState = Option.empty[Any]
      private[this] var patchState = NoPatch

      protected def toolState: Option[Any] = _toolState

      protected def toolState_=(state: Option[Any]): Unit = {
        _toolState        = state
        val r             = canvasComponent.rendering
        r.ttMoveState     = TimelineTool.NoMove
        r.ttResizeState   = TimelineTool.NoResize
        r.ttGainState     = TimelineTool.NoGain
        r.ttFadeState     = TimelineTool.NoFade
        r.ttFunctionState = TimelineTool.NoFunction
        patchState        = NoPatch

        state.foreach {
          case s: TimelineTool.Move      => r.ttMoveState      = s
          case s: TimelineTool.Resize    => r.ttResizeState    = s
          case s: TimelineTool.Gain      => r.ttGainState      = s
          case s: TimelineTool.Fade      => r.ttFadeState      = s
          case s: TimelineTool.Add  => r.ttFunctionState  = s
          case s: TimelineTool.Patch[T]  => patchState         = s
          case _ =>
        }
      }

      object canvasComponent extends Component with DnD[T] {
        protected def timelineModel : TimelineModel = impl.timelineModel
        protected def universe      : Universe[T]   = impl.universe

        private[this] var currentDrop = Option.empty[DnD.Drop[T]]

        font = {
          val f = UIManager.getFont("Slider.font", Locale.US)
          if (f != null) f.deriveFont(math.min(f.getSize2D, 9.5f)) else new Font("SansSerif", Font.PLAIN, 9)
        }
        // setOpaque(true)

        preferredSize = {
          val b = desktop.Util.maximumWindowBounds
          (b.width >> 1, b.height >> 1)
        }

        protected def updateDnD(drop: Option[DnD.Drop[T]]): Unit = {
          currentDrop = drop
          repaint()
        }

        protected def acceptDnD(drop: DnD.Drop[T]): Boolean = performDrop(drop)

        final val rendering: TimelineRenderingImpl = new TimelineRenderingImpl(this, GUI.isDarkSkin)

        override protected def paintComponent(g: Graphics2D): Unit = {
          super.paintComponent(g)
          val w = peer.getWidth
          val h = peer.getHeight
          g.setPaint(rendering.pntBackground)
          g.fillRect(0, 0, w, h)

          import rendering.clipRect
          g.getClipBounds(clipRect)
          val visStart = screenToFrame(clipRect.x).toLong
          val visStop  = screenToFrame(clipRect.x + clipRect.width).toLong + 1 // plus one to avoid glitches

          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

          // warning: iterator, we need to traverse twice!
          val iVal = (visStart, visStop)
          viewRange.filterOverlaps(iVal).foreach { view =>
            view.paintBack(g, impl, rendering)
          }
          viewRange.filterOverlaps(iVal).foreach { view =>
            view.paintFront(g, impl, rendering)
          }

          // --- timeline cursor and selection ---
          paintPosAndSelection(g, h)

          // --- ongoing drag and drop / tools ---
          if (currentDrop.isDefined) currentDrop.foreach { drop =>
            drop.drag match {
              case ad: DnD.AudioDragLike[T] =>
                val track   = screenToModelPos(drop.y)
                val span    = Span(drop.frame, drop.frame + ad.selection.length)
                drawDropFrame(g, modelYStart = track, modelYStop = track + TimelineView.DefaultTrackHeight,
                  span = span, rubber = false)

              case DnD.ObjectDrag(_, view) /* : ObjView.Proc[T] */ =>
                val track   = screenToModelPos(drop.y)
                val length  = defaultDropLength(view, inProgress = true)
                val span    = Span(drop.frame, drop.frame + length)
                drawDropFrame(g, modelYStart = track, modelYStop = track + TimelineView.DefaultTrackHeight,
                  span = span, rubber = false)

              case _ =>
            }
          }

          val funSt = rendering.ttFunctionState
          if (funSt.isValid)
            drawDropFrame(g, modelYStart = funSt.modelYOffset, modelYStop = funSt.modelYOffset + funSt.modelYExtent,
              span = funSt.span, rubber = false)

          val _rubber = rubberState
          if (_rubber.isValid) {
            drawDropFrame(g, modelYStart = _rubber.modelYOffset,
              modelYStop = _rubber.modelYOffset + _rubber.modelYExtent, span = _rubber.span, rubber = true)
          }

          if (patchState.source != null)
            drawPatch(g, patchState)
        }

        private def linkFrame(pv: ObjTimelineView[T]): Long = pv.spanValue match {
          case Span(start, stop) => (start + stop) / 2
          case hs: Span.HasStart => hs.start + (timelineModel.sampleRate * 0.1).toLong
          case _ => 0L
        }

        private def linkY(view: ObjTimelineView[T], input: Boolean): Int =
          if (input)
            modelPosToScreen(view.trackIndex).toInt + 4
          else
            modelPosToScreen(view.trackIndex + view.trackHeight).toInt - 5

//        private def drawLink(g: Graphics2D, source: ProcObjView.Timeline[T], sink: ProcObjView.Timeline[T]): Unit = {
//          val srcFrameC = linkFrame(source)
//          val sinkFrameC = linkFrame(sink)
//          val srcY = linkY(source, input = false)
//          val sinkY = linkY(sink, input = true)
//
//          drawLinkLine(g, srcFrameC, srcY, sinkFrameC, sinkY)
//        }

        @inline private def drawLinkLine(g: Graphics2D, pos1: Long, y1: Int, pos2: Long, y2: Int): Unit = {
          val x1 = frameToScreen(pos1).toFloat
          val x2 = frameToScreen(pos2).toFloat
          // g.drawLine(x1, y1, x2, y2)

          // Yo crazy mama, Wolkenpumpe "5" style
          val ctrlLen = math.min(LinkCtrlPtLen, math.abs(y2 - LinkArrowLen - y1))
          import rendering.shape1
          shape1.reset()
          shape1.moveTo(x1 - 0.5f, y1)
          shape1.curveTo(x1 - 0.5f, y1 + ctrlLen, x2 - 0.5f, y2 - ctrlLen - LinkArrowLen, x2 - 0.5f, y2 - LinkArrowLen)
          g.draw(shape1)
        }

        private def drawPatch(g: Graphics2D, patch: TimelineTool.Patch[T]): Unit = {
          val src = patch.source
          val srcFrameC = linkFrame(src)
          val srcY = linkY(src, input = false)
          val (sinkFrameC, sinkY) = patch.sink match {
            case TimelineTool.Patch.Unlinked(f, y) => (f, y)
            case TimelineTool.Patch.Linked(sink) =>
              val f = linkFrame(sink)
              val y1 = linkY(sink, input = true)
              (f, y1)
          }

          g.setColor(TimelineCanvas2DImpl.colorDropRegionBg)
          val strokeOrig = g.getStroke
          g.setStroke(TimelineCanvas2DImpl.strokeDropRegion)
          drawLinkLine(g, srcFrameC, srcY, sinkFrameC, sinkY)
          g.setStroke(strokeOrig)
        }
      }
    }
  }
}