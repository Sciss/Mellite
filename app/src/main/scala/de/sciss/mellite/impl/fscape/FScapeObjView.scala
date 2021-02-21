/*
 *  FScapeObjView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.fscape

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.fscape.lucre.UGenGraphBuilder.MissingIn
import de.sciss.fscape.stream.Cancelled
import de.sciss.icons.raphael
import de.sciss.log.Level
import de.sciss.lucre.swing.LucreSwing.{defer, deferTx}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.objview.ObjListViewImpl.NonEditable
import de.sciss.mellite.impl.objview.{NoArgsListObjViewFactory, ObjListViewImpl, ObjViewImpl}
import de.sciss.mellite.{AttrMapView, CodeFrame, CodeView, FScapeOutputsView, GUI, ObjListView, ObjView, Shapes, SplitPaneView}
import de.sciss.proc.FScape.GraphObj
import de.sciss.proc.Implicits._
import de.sciss.proc.{Code, FScape, Universe}

import javax.swing.Icon
import javax.swing.undo.UndoableEdit
import scala.concurrent.Promise
import scala.concurrent.stm.Ref
import scala.swing.event.Key
import scala.swing.{Action, Button, Orientation, ProgressBar}
import scala.util.Failure

object FScapeObjView extends NoArgsListObjViewFactory {
  final val DEBUG_LAUNCH = false

  type E[~ <: LTxn[~]] = FScape[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Sparks)
  val prefix        : String    = "FScape"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = FScape
  def category      : String    = ObjView.categComposition

//  private[this] lazy val _init: Unit = ListObjView.addFactory(this)
//
//  def init(): Unit = _init

  def mkListView[T <: Txn[T]](obj: FScape[T])
                             (implicit tx: T): FScapeObjView[T] with ObjListView[T] =
    new Impl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj = FScape[T]()
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, FScape[T]])
    extends FScapeObjView[T]
      with ObjListView /* .Int */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.EmptyRenderer[T]
      with NonEditable[T]
      /* with NonViewable[T] */ {

    override def obj(implicit tx: T): FScape[T] = objH()

    type E[~ <: LTxn[~]] = FScape[~]

    def factory: ObjView.Factory = FScapeObjView

    def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    def openView(parent: Option[Window[T]])
                      (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = codeFrame(obj) // CodeFrame.fscape(obj)
      Some(frame)
    }

    // ---- adapter for editing an FScape's source ----
  }

  private def codeFrame[T <: Txn[T]](obj: FScape[T])
                                    (implicit tx: T, universe: Universe[T],
                                     compiler: Code.Compiler): CodeFrame[T] = {
    import de.sciss.mellite.impl.code.CodeFrameImpl.{make, mkSource}
    val codeObj = mkSource(obj = obj, codeTpe = FScape.Code, key = FScape.attrSource)()
    val objH    = tx.newHandle(obj)
    val code0   = codeObj.value match {
      case cs: FScape.Code => cs
      case other => sys.error(s"FScape source code does not produce fscape.Graph: ${other.tpe.humanName}")
    }

    import de.sciss.fscape.Graph

    val handler = new CodeView.Handler[T, Unit, Graph] {
      def in(): Unit = ()

      def save(in: Unit, out: Graph)(implicit tx: T): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[T, Graph, GraphObj]("Change FScape Graph", obj.graph, GraphObj.newConst[T](out))
      }

      def dispose()(implicit tx: T): Unit = ()
    }

    val renderRef = Ref(Option.empty[FScape.Rendering[T]])

    lazy val ggProgress: ProgressBar = new ProgressBar {
      max = 160
    }

    val viewProgress = View.wrap[T, ProgressBar](ggProgress)

    lazy val actionCancel: swing.Action = new swing.Action(null) {
      def apply(): Unit = {
        import universe.cursor
        cursor.step { implicit tx =>
          renderRef.swap(None)(tx.peer).foreach(_.cancel())
        }
      }
      enabled = false
    }

    val viewCancel = View.wrap[T, Button] {
      GUI.toolButton(actionCancel, raphael.Shapes.Cross, tooltip = "Abort Rendering")
    }

    var debugLaunchP  = Option.empty[Promise[Unit]]
    var debugLaunchC  = 0

    // XXX TODO --- should use custom view so we can cancel upon `dispose`
    val viewRender = View.wrap[T, Button] {
      val actionRender = new swing.Action("Render") { self =>
        def apply(): Unit = {
          import universe.cursor
          cursor.step { implicit tx =>
            if (renderRef.get(tx.peer).isEmpty) {
              val obj       = objH()
              val config    = FScape.defaultConfig.toBuilder
              config.progressReporter = { report =>
                defer {
                  ggProgress.value = (report.total * ggProgress.max).toInt
                }
              }
              // config.blockSize
              // config.nodeBufferSize
              // config.executionContext
              // config.seed
              if (DEBUG_LAUNCH) {
                val pDebug              = Promise[Unit]()
                debugLaunchP            = Some(pDebug)
                debugLaunchC            = 0
                config.debugWaitLaunch  = Some(pDebug.future)
              }

              def finished()(implicit tx: T): Unit = {
                renderRef.set(None)(tx.peer)
                deferTx {
                  actionCancel.enabled  = false
                  self.enabled          = true
                }
              }

              try {
                val rendering = obj.run(config)
                deferTx {
                  actionCancel.enabled = true
                  self        .enabled = false
                }
                /* val obs = */ rendering.reactNow { implicit tx => {
                  case FScape.Rendering.Completed =>
                    finished()
                    rendering.result.foreach {
                      case Failure(Cancelled()) => // ignore
                      case Failure(ex) =>
                        deferTx(ex.printStackTrace())
                      case _ =>
                    }
                  case _ =>
                }}
                renderRef.set(Some(rendering))(tx.peer)
              } catch {
                case MissingIn(key) =>
                  println(s"Attribute input '$key' is missing.")
                //                throw ex
              }
            }
          }
        }
      }
      val ks  = KeyStrokes.shift + Key.F10
      val res = GUI.toolButton(actionRender, Shapes.Sparks)
      Util.addGlobalKey(res, ks)
      res.tooltip = s"Run Rendering (${GUI.keyStrokeText(ks)})"
      res
    }

    val viewDebug = View.wrap[T, Button] {
      new Button("Debug") {
        action = Action(text) {
          renderRef.single.get.foreach { r =>
            val ctrl = r.control
            if (debugLaunchC == 1 && DEBUG_LAUNCH) {
              debugLaunchP.foreach(_.trySuccess(()))
            } else {
              println(ctrl.stats)
              ctrl.debugDotGraph()
            }
            debugLaunchC += 1
          }
        }
      }
    }

    val bottom = viewProgress :: viewCancel :: viewRender :: viewDebug :: Nil

    implicit val undo: UndoManager = UndoManager()
    val outputsView = FScapeOutputsView [T](obj)
    val attrView    = AttrMapView       [T](obj)
    val rightView   = SplitPaneView(attrView, outputsView, Orientation.Vertical)

    import de.sciss.fscape.Log.{control, stream}

    val actToggleControl = Action("Toggle Control Debug") {
      control.level = if (control.level == Level.Debug) Level.Off else Level.Debug
      println(s"Control log level is ${control.level}")
    }
    val actToggleStream = Action("Toggle Stream Debug") {
      stream.level = if (stream.level == Level.Debug) Level.Off else Level.Debug
      println(s"Stream log level is ${stream.level}")
    }

    make(obj, objH, codeObj,
      code0           = code0,
      handler         = Some(handler),
      bottom          = bottom,
      rightViewOpt    = Some(("In/Out", rightView)),
      debugMenuItems  = List(actToggleControl, actToggleStream),
      canBounce       = false   // XXX TODO --- perhaps a standard bounce option would be useful
    )
  }
}
trait FScapeObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = FScape[T]
}