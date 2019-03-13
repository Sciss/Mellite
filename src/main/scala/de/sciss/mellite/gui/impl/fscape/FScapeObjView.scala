/*
 *  FScapeObjView.scala
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
package gui.impl.fscape

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.fscape.lucre.FScape
import de.sciss.fscape.lucre.UGenGraphBuilder.MissingIn
import de.sciss.fscape.stream.Cancelled
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.swing.{View, Window, defer, deferTx}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.objview.ListObjViewImpl.NonEditable
import de.sciss.mellite.gui.impl.objview.{ListObjViewImpl, NoArgsListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.gui.{AttrMapView, CodeFrame, CodeView, FScapeOutputsView, GUI, ListObjView, ObjView, Shapes, SplitPaneView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Code, Universe}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.concurrent.stm.Ref
import scala.swing.event.Key
import scala.swing.{Button, Orientation, ProgressBar}
import scala.util.Failure

object FScapeObjView extends NoArgsListObjViewFactory {
  type E[~ <: stm.Sys[~]] = FScape[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(Shapes.Sparks)
  val prefix        : String    = "FScape"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = FScape
  def category      : String    = ObjView.categComposition

//  private[this] lazy val _init: Unit = ListObjView.addFactory(this)
//
//  def init(): Unit = _init

  def mkListView[S <: Sys[S]](obj: FScape[S])
                             (implicit tx: S#Tx): FScapeObjView[S] with ListObjView[S] =
    new Impl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = FScape[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, FScape[S]])
    extends FScapeObjView[S]
      with ListObjView /* .Int */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.EmptyRenderer[S]
      with NonEditable[S]
      /* with NonViewable[S] */ {

    override def obj(implicit tx: S#Tx): FScape[S] = objH()

    type E[~ <: stm.Sys[~]] = FScape[~]

    def factory: ObjView.Factory = FScapeObjView

    def isViewable = true

    // currently this just opens a code editor. in the future we should
    // add a scans map editor, and a convenience button for the attributes
    def openView(parent: Option[Window[S]])
                      (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.compiler
      val frame = codeFrame(obj) // CodeFrame.fscape(obj)
      Some(frame)
    }

    // ---- adapter for editing an FScape's source ----
  }

  private def codeFrame[S <: Sys[S]](obj: FScape[S])
                                    (implicit tx: S#Tx, universe: Universe[S],
                                     compiler: Code.Compiler): CodeFrame[S] = {
    import de.sciss.mellite.gui.impl.code.CodeFrameImpl.{make, mkSource}
    val codeObj = mkSource(obj = obj, codeId = FScape.Code.id, key = FScape.attrSource,
      init = "// FScape graph function source code\n\n")

    val codeEx0 = codeObj
    val objH    = tx.newHandle(obj)
    val code0   = codeEx0.value match {
      case cs: FScape.Code => cs
      case other => sys.error(s"FScape source code does not produce fscape.Graph: ${other.contextName}")
    }

    import de.sciss.fscape.Graph
    import de.sciss.fscape.lucre.GraphObj

    val handler = new CodeView.Handler[S, Unit, Graph] {
      def in(): Unit = ()

      def save(in: Unit, out: Graph)(implicit tx: S#Tx): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[S, Graph, GraphObj]("Change FScape Graph", obj.graph, GraphObj.newConst[S](out))
      }

      def dispose()(implicit tx: S#Tx): Unit = ()
    }

    val renderRef = Ref(Option.empty[FScape.Rendering[S]])

    lazy val ggProgress: ProgressBar = new ProgressBar {
      max = 160
    }

    val viewProgress = View.wrap[S, ProgressBar](ggProgress)

    lazy val actionCancel: swing.Action = new swing.Action(null) {
      def apply(): Unit = {
        import universe.cursor
        cursor.step { implicit tx =>
          renderRef.swap(None)(tx.peer).foreach(_.cancel())
        }
      }
      enabled = false
    }

    val viewCancel = View.wrap[S, Button] {
      GUI.toolButton(actionCancel, raphael.Shapes.Cross, tooltip = "Abort Rendering")
    }

    // XXX TODO --- should use custom view so we can cancel upon `dispose`
    val viewRender = View.wrap[S, Button] {
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

              def finished()(implicit tx: S#Tx): Unit = {
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

    val viewDebug = View.wrap[S, Button] {
      Button("Debug") {
        renderRef.single.get.foreach { r =>
          val ctrl = r.control
          println(ctrl.stats)
          ctrl.debugDotGraph()
        }
      }
    }

    val bottom = viewProgress :: viewCancel :: viewRender :: viewDebug :: Nil

    implicit val undo: UndoManager = UndoManager()
    val outputsView = FScapeOutputsView [S](obj)
    val attrView    = AttrMapView       [S](obj)
    val rightView   = SplitPaneView(attrView, outputsView, Orientation.Vertical)

    make(obj, objH, codeObj, code0, Some(handler), bottom = bottom, rightViewOpt = Some(("In/Out", rightView)),
      canBounce = false)  // XXX TODO --- perhaps a standard bounce option would be useful
  }
}
trait FScapeObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): FScape[S]
}