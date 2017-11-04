/*
 *  TimelineViewImpl.scala
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
package timeline

import java.awt.{BasicStroke, Font, Graphics2D, RenderingHints, Color => JColor}
import java.util.Locale
import javax.swing.UIManager
import javax.swing.undo.UndoableEdit

import de.sciss.audiowidgets.TimelineModel
import de.sciss.audiowidgets.impl.TimelineModelImpl
import de.sciss.desktop
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{UndoManager, Window}
import de.sciss.fingertree.RangedSeq
import de.sciss.icons.raphael
import de.sciss.lucre.bitemp.BiGroup
import de.sciss.lucre.bitemp.impl.BiGroupImpl
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Cursor, Disposable, IdentifierMap, Obj}
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.{EditFolderInsertObj, EditTimelineInsertObj, Edits}
import de.sciss.mellite.gui.impl.audiocue.AudioCueObjView
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.model.Change
import de.sciss.span.{Span, SpanLike}
import de.sciss.swingplus.ScrollBar
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.gui.TransportView
import de.sciss.synth.proc.impl.AuxContextImpl
import de.sciss.synth.proc.{AudioCue, TimeRef, Timeline, Transport, Workspace}

import scala.concurrent.stm.{Ref, TSet}
import scala.swing.Swing._
import scala.swing.event.ValueChanged
import scala.swing.{Action, BorderPanel, BoxPanel, Component, Orientation, SplitPane}
import scala.util.Try

object TimelineViewImpl {
  private val colrDropRegionBg  = new JColor(0xFF, 0xFF, 0xFF, 0x7F)
  private val strkDropRegion    = new BasicStroke(3f)
  private val strkRubber        = new BasicStroke(3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
    Array[Float](3f, 5f), 0f)
  //  private val colrLink            = new awt.Color(0x80, 0x80, 0x80)
  //  private val strkLink            = new BasicStroke(2f)

  private final val LinkArrowLen  = 0   // 10  ; currently no arrow tip painted
  private final val LinkCtrlPtLen = 20

  private val DEBUG = false // true

  import de.sciss.mellite.{logTimeline => logT}

//  private type TimedProc[S <: Sys[S]] = BiGroup.Entry[S, Proc[S]]

  def apply[S <: Sys[S]](obj: Timeline[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         undo: UndoManager): TimelineView[S] = {
    val sampleRate  = TimeRef.SampleRate
    val tlm         = new TimelineModelImpl(Span(0L, (sampleRate * 60 * 60).toLong), sampleRate)
    tlm.visible     = Span(0L, (sampleRate * 60 * 2).toLong)
    val timeline    = obj
    val timelineH   = tx.newHandle(obj)

    var disposables = List.empty[Disposable[S#Tx]]

    // XXX TODO --- should use TransportView now!

    val viewMap = tx.newInMemoryIDMap[TimelineObjView[S]]
//    val scanMap = tx.newInMemoryIDMap[(String, stm.Source[S#Tx, S#ID])]

    // ugly: the view dispose method cannot iterate over the procs
    // (other than through a GUI driven data structure). thus, it
    // only call pv.disposeGUI() and the procMap and scanMap must be
    // freed directly...
    disposables ::= viewMap
//    disposables ::= scanMap
    val transport = Transport[S](Mellite.auralSystem) // = proc.Transport [S, workspace.I](group, sampleRate = sampleRate)
    disposables ::= transport
    // val auralView = proc.AuralPresentation.run[S](transport, Mellite.auralSystem, Some(Mellite.sensorSystem))
    // disposables ::= auralView
    transport.addObject(obj)

    // val globalSelectionModel = SelectionModel[S, ProcView[S]]
    val selectionModel = SelectionModel[S, TimelineObjView[S]]
    val global = GlobalProcsView(timeline, selectionModel)
    disposables ::= global

    val transportView = TransportView(transport, tlm, hasMillis = true, hasLoop = true)
    val tlView = new Impl[S](timelineH, viewMap, /* scanMap, */ tlm, selectionModel, global, transportView, tx)

    val obsTimeline = timeline.changed.react { implicit tx => upd =>
      upd.changes.foreach {
        case BiGroup.Added(span, timed) =>
          if (DEBUG) println(s"Added   $span, $timed")
          tlView.objAdded(span, timed, repaint = true)

        case BiGroup.Removed(span, timed) =>
          if (DEBUG) println(s"Removed $span, $timed")
          tlView.objRemoved(span, timed)

        case BiGroup.Moved(spanChange, timed) =>
          if (DEBUG) println(s"Moved   $timed, $spanChange")
          tlView.objMoved(timed, spanCh = spanChange, trackCh = None)
      }
    }
    disposables ::= obsTimeline

    tlView.disposables.set(disposables)(tx.peer)

    deferTx(tlView.guiInit())

    // must come after guiInit because views might call `repaint` in the meantime!
    timeline.iterator.foreach { case (span, seq) =>
      seq.foreach { timed =>
        tlView.objAdded(span, timed, repaint = false)
      }
    }

    tlView
  }

  private final class Impl[S <: Sys[S]](val timelineH: stm.Source[S#Tx, Timeline[S]],
                                        val viewMap: TimelineObjView.Map[S],
//                                        val scanMap: ProcObjView.ScanMap[S],
                                        val timelineModel: TimelineModel,
                                        val selectionModel: SelectionModel[S, TimelineObjView[S]],
                                        val globalView: GlobalProcsView[S],
                                        val transportView: TransportView[S], tx0: S#Tx)
                                       (implicit val workspace: Workspace[S], val cursor: Cursor[S],
                                        val undoManager: UndoManager)
    extends TimelineActions[S]
      with TimelineView[S]
      with ComponentHolder[Component]
      with TimelineObjView.Context[S]
      with AuxContextImpl[S] {

    impl =>

    import cursor.step

    private[this] var viewRange = RangedSeq.empty[TimelineObjView[S], Long]
    private[this] val viewSet   = TSet     .empty[TimelineObjView[S]]

    var canvas: TimelineTrackCanvasImpl[S] = _
    val disposables = Ref(List.empty[Disposable[S#Tx]])

    protected val auxMap: IdentifierMap[S#ID, S#Tx, Any]                      = tx0.newInMemoryIDMap
    protected val auxObservers: IdentifierMap[S#ID, S#Tx, List[AuxObserver]]  = tx0.newInMemoryIDMap

    private lazy val toolCursor   = TrackTool.cursor  [S](canvas)
    private lazy val toolMove     = TrackTool.move    [S](canvas)
    private lazy val toolResize   = TrackTool.resize  [S](canvas)
    private lazy val toolGain     = TrackTool.gain    [S](canvas)
    private lazy val toolMute     = TrackTool.mute    [S](canvas)
    private lazy val toolFade     = TrackTool.fade    [S](canvas)
    private lazy val toolFunction = TrackTool.function[S](canvas, this)
    private lazy val toolPatch    = TrackTool.patch   [S](canvas)
    private lazy val toolAudition = TrackTool.audition[S](canvas, this)

    def timeline  (implicit tx: S#Tx): Timeline[S] = timelineH()
    def plainGroup(implicit tx: S#Tx): Timeline[S] = timeline

    def window: Window = component.peer.getClientProperty("de.sciss.mellite.Window").asInstanceOf[Window]

    def dispose()(implicit tx: S#Tx): Unit = {
      deferTx {
        viewRange = RangedSeq.empty
      }
      disposables.swap(Nil)(tx.peer).foreach(_.dispose())
      viewSet.foreach(_.dispose())(tx.peer)
      clearSet(viewSet)
      // these two are already included in `disposables`:
      // viewMap.dispose()
      // scanMap.dispose()
    }

    private def clearSet[A](s: TSet[A])(implicit tx: S#Tx): Unit =
      s.retain(_ => false)(tx.peer) // no `clear` method

    private def debugCheckConsistency(info: => String)(implicit tx: S#Tx): Unit = if (DEBUG) {
      val check = BiGroupImpl.verifyConsistency(plainGroup, reportOnly = true)
      check.foreach { msg =>
        println(info)
        println(msg)
        sys.error("Rollback")
      }
    }

    def guiInit(): Unit = {
      canvas = new View
      val ggVisualBoost = GUI.boostRotary()(canvas.trackTools.visualBoost = _)

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
          TrackTools.palette(canvas.trackTools, Vector(
            toolCursor, toolMove, toolResize, toolGain, toolFade /* , toolSlide*/ ,
            toolMute, toolAudition, toolFunction, toolPatch)),
          HStrut(4),
          ggAttr,
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
          if (hadSelectedObjects != hasSome) {
            hadSelectedObjects = hasSome
            actionAttr                .enabled = hasSome
            actionSplitObjects        .enabled = hasSome
            actionCleanUpObjects      .enabled = hasSome
            actionAlignObjectsToCursor.enabled = hasSome
          }
      }

      var hasSelectedSpan = false

      timelineModel.addListener {
        case TimelineModel.Selection(_, span) if span.before.isEmpty != span.now.isEmpty =>
          val hasSome = span.now.nonEmpty
          if (hasSelectedSpan != hasSome) {
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
        add(transportPane, North)
        add(pane2, Center)
        add(ggTrackPos, East)
        // add(globalView.component, West  )
      }

      component = pane
      // DocumentViewHandler.instance.add(this)
    }

    private def repaintAll(): Unit = canvas.canvasComponent.repaint()

    def objAdded(span: SpanLike, timed: BiGroup.Entry[S, Obj[S]], repaint: Boolean)(implicit tx: S#Tx): Unit = {
      logT(s"objAdded($span / ${TimeRef.spanToSecs(span)}, $timed)")
      // timed.span
      // val proc = timed.value

      // val pv = ProcView(timed, viewMap, scanMap)
      val view = TimelineObjView(timed, this)
      viewMap.put(timed.id, view)
      viewSet.add(view)(tx.peer)

      def doAdd(): Unit = {
        view match {
          case pv: ProcObjView.Timeline[S] if pv.isGlobal =>
            globalView.add(pv)
          case _ =>
            viewRange += view
            if (repaint) repaintAll() // XXX TODO: optimize dirty rectangle
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

    private def warnViewNotFound(action: String, timed: BiGroup.Entry[S, Obj[S]]): Unit =
      Console.err.println(s"Warning: Timeline - $action. View for object $timed not found.")

    def objRemoved(span: SpanLike, timed: BiGroup.Entry[S, Obj[S]])(implicit tx: S#Tx): Unit = {
      logT(s"objRemoved($span, $timed)")
      val id = timed.id
      viewMap.get(id).fold {
        warnViewNotFound("remove", timed)
      } { view =>
        viewMap.remove(id)
        viewSet.remove(view)(tx.peer)
        deferTx {
          view match {
            case pv: ProcObjView.Timeline[S] if pv.isGlobal => globalView.remove(pv)
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
    def objMoved(timed: BiGroup.Entry[S, Obj[S]], spanCh: Change[SpanLike], trackCh: Option[(Int, Int)])
                (implicit tx: S#Tx): Unit = {
      logT(s"objMoved(${spanCh.before} / ${TimeRef.spanToSecs(spanCh.before)} -> ${spanCh.now} / ${TimeRef.spanToSecs(spanCh.now)}, $timed)")
      viewMap.get(timed.id).fold {
        warnViewNotFound("move", timed)
      } { view =>
        deferTx {
          view match {
            case pv: ProcObjView.Timeline[S] if pv.isGlobal => globalView.remove(pv)
            case _ => viewRange -= view
          }

          if (spanCh.isSignificant) view.spanValue = spanCh.now
          trackCh.foreach { case (idx, h) =>
            view.trackIndex = idx
            view.trackHeight = h
          }

          view match {
            case pv: ProcObjView.Timeline[S] if pv.isGlobal => globalView.add(pv)
            case _ =>
              viewRange += view
              repaintAll() // XXX TODO: optimize dirty rectangle
          }
        }
      }
    }

    private def objUpdated(view: TimelineObjView[S])(implicit tx: S#Tx): Unit = deferTx {
      //      if (view.isGlobal)
      //        globalView.updated(view)
      //      else
      repaintAll() // XXX TODO: optimize dirty rectangle
    }

    // TODO - this could be defined by the view?
    // call on EDT!
    private def defaultDropLength(view: ObjView[S], inProgress: Boolean): Long = {
      val d = view match {
        case a: AudioCueObjView[S] =>
          val v = a.value
          (v.numFrames * TimeRef.SampleRate / v.sampleRate).toLong
        case _: ProcObjView[S] =>
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

    private def insertAudioRegion(drop: DnD.Drop[S], drag: DnD.AudioDragLike[S],
                                  audioCue: AudioCue.Obj[S])(implicit tx: S#Tx): Option[UndoableEdit] =
      plainGroup.modifiableOption.map { groupM =>
        logT(s"insertAudioRegion($drop, ${drag.selection}, $audioCue)")
        val tlSpan = Span(drop.frame, drop.frame + drag.selection.length)
        val (span, obj) = ProcActions.mkAudioRegion(time = tlSpan,
          audioCue = audioCue, gOffset = drag.selection.start /*, bus = None */) // , bus = ad.bus.map(_.apply().entity))
        val track = canvas.screenToTrack(drop.y)
        obj.attr.put(TimelineObjView.attrTrackIndex, IntObj.newVar(IntObj.newConst(track)))
        val edit = EditTimelineInsertObj("Insert Audio Region", groupM, span, obj)
        edit
      }

    private def performDrop(drop: DnD.Drop[S]): Boolean = {
      def withRegions[A](fun: S#Tx => List[TimelineObjView[S]] => Option[A]): Option[A] =
        canvas.findRegion(drop.frame, canvas.screenToTrack(drop.y)).flatMap { hitRegion =>
          val regions = if (selectionModel.contains(hitRegion)) selectionModel.iterator.toList else hitRegion :: Nil
          step { implicit tx =>
            fun(tx)(regions)
          }
        }

      def withProcRegions[A](fun: S#Tx => List[ProcObjView[S]] => Option[A]): Option[A] =
        canvas.findRegion(drop.frame, canvas.screenToTrack(drop.y)).flatMap {
          case hitRegion: ProcObjView[S] =>
            val regions = if (selectionModel.contains(hitRegion)) {
              selectionModel.iterator.collect {
                case pv: ProcObjView[S] => pv
              }.toList
            } else hitRegion :: Nil

            step { implicit tx =>
              fun(tx)(regions)
            }
          case _ => None
        }

      // println(s"performDrop($drop)")

      val editOpt: Option[UndoableEdit] = drop.drag match {
        case ad: DnD.AudioDrag[S] =>
          step { implicit tx =>
            insertAudioRegion(drop, ad, ad.source())
          }

        case ed: DnD.ExtAudioRegionDrag[S] =>
          val file = ed.file
          val resOpt = step { implicit tx =>
            val ex = ObjectActions.findAudioFile(workspace.rootH(), file)
            ex.flatMap { grapheme =>
              insertAudioRegion(drop, ed, grapheme)
            }
          }

          resOpt.orElse[UndoableEdit] {
            val tr = Try(AudioFile.readSpec(file)).toOption
            tr.flatMap { spec =>
              ActionArtifactLocation.query[S](workspace.rootH, file).flatMap { either =>
                step { implicit tx =>
                  ActionArtifactLocation.merge(either).flatMap { case (list0, locM) =>
                    val folder = workspace.rootH()
                    // val obj   = ObjectActions.addAudioFile(elems, elems.size, loc, file, spec)
                    val obj = ObjectActions.mkAudioFile(locM, file, spec)
                    val edits0 = list0.map(obj => EditFolderInsertObj("Location", folder, folder.size, obj)).toList
                    val edits1 = edits0 :+ EditFolderInsertObj("Audio File", folder, folder.size, obj)
                    val edits2 = insertAudioRegion(drop, ed, obj).fold(edits1)(edits1 :+ _)
                    CompoundEdit(edits2, "Insert Audio Region")
                  }
                }
              }
            }
          }

        case DnD.ObjectDrag(_, view: IntObjView[S]) => withRegions { implicit tx => regions =>
          val intExpr = view.obj
          Edits.setBus[S](regions.map(_.obj), intExpr)
        }

        case DnD.ObjectDrag(_, view: CodeObjView[S]) => withProcRegions { implicit tx => regions =>
          val codeElem = view.obj
          import Mellite.compiler
          Edits.setSynthGraph[S](regions.map(_.obj), codeElem)
        }

        case DnD.ObjectDrag(ws, view: AudioCueObjView[S]) =>
          val length  = defaultDropLength(view, inProgress = false)
          val span    = Span(0L, length)
          val ad      = DnD.AudioDrag(ws, view.objH, span)
          step { implicit tx =>
            insertAudioRegion(drop, ad, ad.source())
          }

        case DnD.ObjectDrag(_, view /* : ObjView.Proc[S] */) => step { implicit tx =>
          plainGroup.modifiableOption.map { group =>
            val length  = defaultDropLength(view, inProgress = false)
            val span    = Span(drop.frame, drop.frame + length)
            val spanEx  = SpanLikeObj.newVar[S](SpanLikeObj.newConst(span))
            EditTimelineInsertObj(s"Insert ${view.humanName}", group, spanEx, view.obj)
          }
          // CompoundEdit(edits, "Insert Objects")
        }

        case pd: DnD.GlobalProcDrag[S] => withProcRegions { implicit tx => regions =>
          val in = pd.source()
          val edits = regions.flatMap { pv =>
            val out = pv.obj
            Edits.linkOrUnlink[S](out, in)
          }
          CompoundEdit(edits, "Link Global Proc")
        }

        case _ => None
      }

      editOpt.foreach(undoManager.add)
      editOpt.isDefined
    }

    private final class View extends TimelineTrackCanvasImpl[S] {
      canvasImpl =>

      def timelineModel : TimelineModel                         = impl.timelineModel
      def selectionModel: SelectionModel[S, TimelineObjView[S]] = impl.selectionModel

      def timeline(implicit tx: S#Tx): Timeline[S] = impl.plainGroup

      def iterator: Iterator[TimelineObjView[S]] = viewRange.iterator

      def intersect(span: Span.NonVoid): Iterator[TimelineObjView[S]] = {
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

      def findRegion(pos: Long, hitTrack: Int): Option[TimelineObjView[S]] = {
        val span = Span(pos, pos + 1)
        val regions = intersect(span)
        regions.find(pv => pv.trackIndex <= hitTrack && (pv.trackIndex + pv.trackHeight) > hitTrack)
      }

      def findRegions(r: TrackTool.Rectangular): Iterator[TimelineObjView[S]] = {
        val regions = intersect(r.span)
        regions.filter(pv => pv.trackIndex < r.trackIndex + r.trackHeight && (pv.trackIndex + pv.trackHeight) > r.trackIndex)
      }

      protected def commitToolChanges(value: Any): Unit = {
        logT(s"Commit tool changes $value")
        val editOpt = step { implicit tx =>
          value match {
            case t: TrackTool.Cursor => toolCursor commit t
            case t: TrackTool.Move =>
              // println("\n----BEFORE----")
              // println(group.debugPrint)
              val res = toolMove.commit(t)
              // println("\n----AFTER----")
              // println(group.debugPrint)
              debugCheckConsistency(s"Move $t")
              res

            case t: TrackTool.Resize =>
              val res = toolResize commit t
              debugCheckConsistency(s"Resize $t")
              res

            case t: TrackTool.Gain => toolGain commit t
            case t: TrackTool.Mute => toolMute commit t
            case t: TrackTool.Fade => toolFade commit t
            case t: TrackTool.Function => toolFunction commit t
            case t: TrackTool.Patch[S] => toolPatch commit t
            case _ => None
          }
        }
        editOpt.foreach(undoManager.add)
      }

      private val NoPatch = TrackTool.Patch[S](null, null)
      // not cool
      private var _toolState = Option.empty[Any]
      private var patchState = NoPatch

      protected def toolState: Option[Any] = _toolState

      protected def toolState_=(state: Option[Any]): Unit = {
        _toolState        = state
        val r             = canvasComponent.rendering
        r.ttMoveState     = TrackTool.NoMove
        r.ttResizeState   = TrackTool.NoResize
        r.ttGainState     = TrackTool.NoGain
        r.ttFadeState     = TrackTool.NoFade
        r.ttFunctionState = TrackTool.NoFunction
        patchState        = NoPatch

        state.foreach {
          case s: TrackTool.Move      => r.ttMoveState      = s
          case s: TrackTool.Resize    => r.ttResizeState    = s
          case s: TrackTool.Gain      => r.ttGainState      = s
          case s: TrackTool.Fade      => r.ttFadeState      = s
          case s: TrackTool.Function  => r.ttFunctionState  = s
          case s: TrackTool.Patch[S]  => patchState         = s
          case _ =>
        }
      }

      object canvasComponent extends Component with DnD[S] {
        protected def timelineModel : TimelineModel = impl.timelineModel
        protected def workspace     : Workspace[S]  = impl.workspace

        private[this] var currentDrop = Option.empty[DnD.Drop[S]]

        font = {
          val f = UIManager.getFont("Slider.font", Locale.US)
          if (f != null) f.deriveFont(math.min(f.getSize2D, 9.5f)) else new Font("SansSerif", Font.PLAIN, 9)
        }
        // setOpaque(true)

        preferredSize = {
          val b = desktop.Util.maximumWindowBounds
          (b.width >> 1, b.height >> 1)
        }

        protected def updateDnD(drop: Option[DnD.Drop[S]]): Unit = {
          currentDrop = drop
          repaint()
        }

        protected def acceptDnD(drop: DnD.Drop[S]): Boolean = performDrop(drop)

        final val rendering: TimelineRenderingImpl = new TimelineRenderingImpl(this, Mellite.isDarkSkin)

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
              case ad: DnD.AudioDragLike[S] =>
                val track   = screenToTrack(drop.y)
                val span    = Span(drop.frame, drop.frame + ad.selection.length)
                drawDropFrame(g, track, TimelineView.DefaultTrackHeight, span, rubber = false)

              case DnD.ObjectDrag(_, view) /* : ObjView.Proc[S] */ =>
                val track   = screenToTrack(drop.y)
                val length  = defaultDropLength(view, inProgress = true)
                val span    = Span(drop.frame, drop.frame + length)
                drawDropFrame(g, track, TimelineView.DefaultTrackHeight, span, rubber = false)

              case _ =>
            }
          }

          val funSt = rendering.ttFunctionState
          if (funSt.isValid)
            drawDropFrame(g, funSt.trackIndex, funSt.trackHeight, funSt.span, rubber = false)

          if (rubberState.isValid)
            drawDropFrame(g, rubberState.trackIndex, rubberState.trackHeight, rubberState.span, rubber = true)

          if (patchState.source != null)
            drawPatch(g, patchState)
        }

        private def linkFrame(pv: ProcObjView.Timeline[S]): Long = pv.spanValue match {
          case Span(start, stop) => (start + stop) / 2
          case hs: Span.HasStart => hs.start + (timelineModel.sampleRate * 0.1).toLong
          case _ => 0L
        }

        private def linkY(view: ProcObjView.Timeline[S], input: Boolean): Int =
          if (input)
            trackToScreen(view.trackIndex) + 4
          else
            trackToScreen(view.trackIndex + view.trackHeight) - 5

//        private def drawLink(g: Graphics2D, source: ProcObjView.Timeline[S], sink: ProcObjView.Timeline[S]): Unit = {
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

        private def drawPatch(g: Graphics2D, patch: TrackTool.Patch[S]): Unit = {
          val src = patch.source
          val srcFrameC = linkFrame(src)
          val srcY = linkY(src, input = false)
          val (sinkFrameC, sinkY) = patch.sink match {
            case TrackTool.Patch.Unlinked(f, y) => (f, y)
            case TrackTool.Patch.Linked(sink) =>
              val f = linkFrame(sink)
              val y1 = linkY(sink, input = true)
              (f, y1)
          }

          g.setColor(colrDropRegionBg)
          val strkOrig = g.getStroke
          g.setStroke(strkDropRegion)
          drawLinkLine(g, srcFrameC, srcY, sinkFrameC, sinkY)
          g.setStroke(strkOrig)
        }

        private def drawDropFrame(g: Graphics2D, trackIndex: Int, trackHeight: Int, span: Span,
                                  rubber: Boolean): Unit = {
          val x1 = frameToScreen(span.start).toInt
          val x2 = frameToScreen(span.stop).toInt
          g.setColor(colrDropRegionBg)
          val strkOrig = g.getStroke
          g.setStroke(if (rubber) strkRubber else strkDropRegion)
          val y = trackToScreen(trackIndex)
          val x1b = math.min(x1 + 1, x2)
          val x2b = math.max(x1b, x2 - 1)
          g.drawRect(x1b, y + 1, x2b - x1b, trackToScreen(trackIndex + trackHeight) - y)
          g.setStroke(strkOrig)
        }
      }
    }
  }
}