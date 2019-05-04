/*
 *  TimelineViewImpl.scala
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

package de.sciss.mellite.gui.impl.timeline

import java.awt.datatransfer.Transferable
import java.awt.{Font, Graphics2D, RenderingHints}
import java.util.Locale

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.fingertree.RangedSeq
import de.sciss.lucre.bitemp.BiGroup
import de.sciss.lucre.bitemp.impl.BiGroupImpl
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, IdentifierMap, Obj}
import de.sciss.lucre.swing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.{EditFolderInsertObj, EditTimelineInsertObj, Edits}
import de.sciss.mellite.gui.impl.audiocue.AudioCueObjView
import de.sciss.mellite.gui.impl.component.DragSourceButton
import de.sciss.mellite.gui.impl.objview.{CodeObjView, IntObjView}
import de.sciss.mellite.gui.impl.proc.ProcObjView
import de.sciss.mellite.gui.impl.{TimelineCanvas2DImpl, TimelineViewBaseImpl}
import de.sciss.mellite.gui.{ActionArtifactLocation, BasicTool, GUI, GlobalProcsView, ObjView, SelectionModel, TimelineObjView, TimelineTool, TimelineTools, TimelineView}
import de.sciss.mellite.{Mellite, ObjectActions, ProcActions}
import de.sciss.model.Change
import de.sciss.span.{Span, SpanLike}
import de.sciss.swingplus.ScrollBar
import de.sciss.synth.io.AudioFile
import de.sciss.synth.proc.gui.TransportView
import de.sciss.synth.proc.impl.AuxContextImpl
import de.sciss.synth.proc.{AudioCue, TimeRef, Timeline, Transport, Universe}
import javax.swing.UIManager
import javax.swing.undo.UndoableEdit

import scala.concurrent.stm.{Ref, TSet}
import scala.math.{max, min}
import scala.swing.Swing._
import scala.swing.event.ValueChanged
import scala.swing.{BorderPanel, BoxPanel, Component, Orientation, SplitPane}
import scala.util.Try

object TimelineViewImpl {
  private final val LinkArrowLen  = 0   // 10  ; currently no arrow tip painted
  private final val LinkCtrlPtLen = 20

  private val DEBUG = false // true

  import de.sciss.mellite.{logTimeline => logT}

//  private type TimedProc[S <: Sys[S]] = BiGroup.Entry[S, Proc[S]]

  def apply[S <: Sys[S]](obj: Timeline[S])
                        (implicit tx: S#Tx, universe: Universe[S],
                         undo: UndoManager): TimelineView[S] = {
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

    var disposables = List.empty[Disposable[S#Tx]]

    // XXX TODO --- should use TransportView now!

    val viewMap = tx.newInMemoryIdMap[TimelineObjView[S]]
//    val scanMap = tx.newInMemoryIdMap[(String, stm.Source[S#Tx, S#Id])]

    // ugly: the view dispose method cannot iterate over the proc objects
    // (other than through a GUI driven data structure). thus, it
    // only call pv.disposeGUI() and the procMap and scanMap must be
    // freed directly...
    disposables ::= viewMap
//    disposables ::= scanMap
    val transport = Transport[S](universe) // = proc.Transport [S, workspace.I](group, sampleRate = sampleRate)
    disposables ::= transport
    // val auralView = proc.AuralPresentation.run[S](transport, Mellite.auralSystem, Some(Mellite.sensorSystem))
    // disposables ::= auralView
    transport.addObject(obj)

    // val globalSelectionModel = SelectionModel[S, ProcView[S]]
    val selectionModel = SelectionModel[S, TimelineObjView[S]]
    val global = GlobalProcsView(timeline, selectionModel)
    disposables ::= global

    import universe.cursor
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

    tlView.init()

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
                                        val timelineModel: TimelineModel.Modifiable,
                                        val selectionModel: SelectionModel[S, TimelineObjView[S]],
                                        val globalView: GlobalProcsView[S],
                                        val transportView: TransportView[S], tx0: S#Tx)
    extends TimelineView[S]
      with TimelineViewBaseImpl[S, Int, TimelineObjView[S]]
      with TimelineActions[S]
      with ComponentHolder[Component]
      with TimelineObjView.Context[S]
      with AuxContextImpl[S] {

    impl =>

    type C = Component

    implicit val universe: Universe[S] = globalView.universe

    def undoManager: UndoManager = globalView.undoManager

    private[this] var viewRange = RangedSeq.empty[TimelineObjView[S], Long]
    private[this] val viewSet   = TSet     .empty[TimelineObjView[S]]

    var canvas      : TimelineTrackCanvasImpl[S]  = _
    val disposables : Ref[List[Disposable[S#Tx]]] = Ref(Nil)

    protected val auxMap      : IdentifierMap[S#Id, S#Tx, Any]                = tx0.newInMemoryIdMap
    protected val auxObservers: IdentifierMap[S#Id, S#Tx, List[AuxObserver]]  = tx0.newInMemoryIdMap

    private[this] lazy val toolCursor   = TimelineTool.cursor  [S](canvas)
    private[this] lazy val toolMove     = TimelineTool.move    [S](canvas)
    private[this] lazy val toolResize   = TimelineTool.resize  [S](canvas)
    private[this] lazy val toolGain     = TimelineTool.gain    [S](canvas)
    private[this] lazy val toolMute     = TimelineTool.mute    [S](canvas)
    private[this] lazy val toolFade     = TimelineTool.fade    [S](canvas)
    private[this] lazy val toolFunction = TimelineTool.function[S](canvas, this)
    private[this] lazy val toolPatch    = TimelineTool.patch   [S](canvas)
    private[this] lazy val toolAudition = TimelineTool.audition[S](canvas, this)

    def timeline  (implicit tx: S#Tx): Timeline[S] = timelineH()
    def plainGroup(implicit tx: S#Tx): Timeline[S] = timeline

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

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      this
    }

    override protected def guiInit(): Unit = {
      super.guiInit()

      canvas = new View
      val ggVisualBoost = GUI.boostRotary()(canvas.timelineTools.visualBoost = _)

      val ggDragObject = new DragSourceButton() {
        protected def createTransferable(): Option[Transferable] = {
          None
//          val spOpt = timelineModel.selection match {
//            case sp0: Span if sp0.nonEmpty => Some(sp0)
//            case _ => timelineModel.bounds match {
//              case sp0: Span => Some(sp0)
//              case _ => None
//            }
//          }
//          spOpt.map { sp =>
//            val drag  = timeline.DnD.AudioDrag(universe, holder, selection = sp)
//            val t1    = DragAndDrop.Transferable(timeline.DnD.flavor)(drag)
//            val t2    = DragAndDrop.Transferable.files(snapshot.artifact)
//            DragAndDrop.Transferable.seq(t1, t2)
//          }
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
          if (hadSelectedObjects != hasSome) {
            hadSelectedObjects = hasSome
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
        add(topPane, North)
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
              if (newBounds != oldBounds) {
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
        val track = canvas.screenToModelPos(drop.y)
        obj.attr.put(TimelineObjView.attrTrackIndex, IntObj.newVar(IntObj.newConst(track)))
        val edit = EditTimelineInsertObj("Insert Audio Region", groupM, span, obj)
        edit
      }

    private def performDrop(drop: DnD.Drop[S]): Boolean = {
      def withRegions[A](fun: S#Tx => List[TimelineObjView[S]] => Option[A]): Option[A] =
        canvas.findChildView(drop.frame, canvas.screenToModelPos(drop.y)).flatMap { hitRegion =>
          val regions = if (selectionModel.contains(hitRegion)) selectionModel.iterator.toList else hitRegion :: Nil
          cursor.step { implicit tx =>
            fun(tx)(regions)
          }
        }

      def withProcRegions[A](fun: S#Tx => List[ProcObjView[S]] => Option[A]): Option[A] =
        canvas.findChildView(drop.frame, canvas.screenToModelPos(drop.y)).flatMap {
          case hitRegion: ProcObjView[S] =>
            val regions = if (selectionModel.contains(hitRegion)) {
              selectionModel.iterator.collect {
                case pv: ProcObjView[S] => pv
              }.toList
            } else hitRegion :: Nil

            cursor.step { implicit tx =>
              fun(tx)(regions)
            }
          case _ => None
        }

      // println(s"performDrop($drop)")

      val editOpt: Option[UndoableEdit] = drop.drag match {
        case ad: DnD.AudioDrag[S] =>
          cursor.step { implicit tx =>
            insertAudioRegion(drop, ad, ad.source())
          }

        case ed: DnD.ExtAudioRegionDrag[S] =>
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
              ActionArtifactLocation.query[S](file)(implicit tx => universe.workspace.root).flatMap { either =>
                cursor.step { implicit tx =>
                  ActionArtifactLocation.merge(either).flatMap { case (list0, locM) =>
                    val folder = universe.workspace.root
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
          cursor.step { implicit tx =>
            insertAudioRegion(drop, ad, ad.source())
          }

        case DnD.ObjectDrag(_, view /* : ObjView.Proc[S] */) => cursor.step { implicit tx =>
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

      def findChildView(pos: Long, modelY: Int): Option[TimelineObjView[S]] = {
        val span      = Span(pos, pos + 1)
        val children  = intersect(span)
        children.find(pv => pv.trackIndex <= modelY && (pv.trackIndex + pv.trackHeight) > modelY)
      }

      def findChildViews(r: BasicTool.Rectangular[Int]): Iterator[TimelineObjView[S]] = {
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
            case t: TimelineTool.Patch[S] => toolPatch commit t
            case _ => None
          }
        }
        editOpt.foreach(undoManager.add)
      }

      private[this] val NoPatch = TimelineTool.Patch[S](null, null)
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
          case s: TimelineTool.Patch[S]  => patchState         = s
          case _ =>
        }
      }

      object canvasComponent extends Component with DnD[S] {
        protected def timelineModel : TimelineModel = impl.timelineModel
        protected def universe      : Universe[S]   = impl.universe

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
                val track   = screenToModelPos(drop.y)
                val span    = Span(drop.frame, drop.frame + ad.selection.length)
                drawDropFrame(g, modelYStart = track, modelYStop = track + TimelineView.DefaultTrackHeight,
                  span = span, rubber = false)

              case DnD.ObjectDrag(_, view) /* : ObjView.Proc[S] */ =>
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

        private def linkFrame(pv: ProcObjView.Timeline[S]): Long = pv.spanValue match {
          case Span(start, stop) => (start + stop) / 2
          case hs: Span.HasStart => hs.start + (timelineModel.sampleRate * 0.1).toLong
          case _ => 0L
        }

        private def linkY(view: ProcObjView.Timeline[S], input: Boolean): Int =
          if (input)
            modelPosToScreen(view.trackIndex).toInt + 4
          else
            modelPosToScreen(view.trackIndex + view.trackHeight).toInt - 5

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

        private def drawPatch(g: Graphics2D, patch: TimelineTool.Patch[S]): Unit = {
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