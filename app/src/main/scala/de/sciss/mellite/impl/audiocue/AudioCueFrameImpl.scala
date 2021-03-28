/*
 *  AudioCueFrameImpl.scala
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

package de.sciss.mellite.impl.audiocue

import de.sciss.asyncfile.Ops._
import de.sciss.desktop.Menu
import de.sciss.file._
import de.sciss.kollflitz.Vec
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{DoubleObj, IntVector}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{AudioCueFrame, AudioCueView, Prefs}
import de.sciss.proc.{AudioCue, Tag, Universe}

import java.net.URI
import scala.concurrent.Future
import scala.swing.event.{UIElementMoved, UIElementResized, UIElementShown}
import scala.swing.{Action, CheckMenuItem, Reactions, UIElement}
import scala.util.Try

object AudioCueFrameImpl {
  def apply[T <: Txn[T]](obj: AudioCue.Obj[T])
                        (implicit tx: T, universe: Universe[T]): AudioCueFrame[T] = {
    val afv       = AudioCueView(obj)
    val name0     = CellView.name(obj)
    val file      = obj.value.artifact
    val fileName  = file.base
    import de.sciss.equal.Implicits._
    val name      = name0.map { n =>
      if (n === fileName) n else s"$n- $fileName"
    }
    val bounds0 = (for {
      attr    <- tx.attrMapOption(obj)
      tag     <- attr.$[Tag]("view")
      tAttr   <- tx.attrMapOption(tag)
      bounds  <- tAttr.$[IntVector](StateKey_WindowBounds)
    } yield bounds.value).getOrElse(Vec.empty)

    val res       = new Impl(/* doc, */ view = afv, name = name, uri = file, bounds0 = bounds0)
    res.init()
    res
  }

  private final val StateKey_WindowBounds = "window-bounds"

  private final class Impl[T <: Txn[T]](/* val document: Workspace[T], */ val view: AudioCueView[T],
                                        name: CellView[T, String], uri: URI, bounds0: Vec[Int])
    extends WindowImpl[T](name)
    with AudioCueFrame[T] {

    @volatile
    private var lastViewState = Map.empty[String, Any]

    @volatile
    private var stateBounds: Vec[Int] = bounds0
    private var dirtyBounds = false

    override protected def packAndPlace: Boolean = stateBounds.size != 4

    private def saveViewState: Boolean = Prefs.viewSaveState.getOrElse(false)

    override protected def performClose(): Future[Unit] = {
      if (saveViewState) {
        val state0  = view.viewState
        val state1  = if (!dirtyBounds) state0 else state0 + ((StateKey_WindowBounds, stateBounds))
        lastViewState = state1
      }
      super.performClose()
    }

    override def dispose()(implicit tx: T): Unit = {
      if (!wasDisposed && lastViewState.nonEmpty) {
        val attr  = view.obj.attr
        val tag   = attr.$[Tag]("view").getOrElse {
          val t = Tag[T]()
          attr.put("view", t)
          t
        }
        val tAttr = tag.attr
        lastViewState.foreach { case (key, value) =>
          value match {
            case v: Double =>
              tAttr.$[DoubleObj](key) match {
                case Some(DoubleObj.Var(vr)) => vr() = v
                case _ =>
                  tAttr.put(key, DoubleObj.newVar[T](v))
              }

            case sq: Seq[_] =>
              if (sq.forall(_.isInstanceOf[Int])) {
                val v = sq.asInstanceOf[Seq[Int]].toIndexedSeq
                tAttr.$[IntVector](key) match {
                  case Some(IntVector.Var(vr)) => vr() = v
                  case _ =>
                    tAttr.put(key, IntVector.newVar[T](v))
                }
              }

            case _ =>
              sys.error(s"Unsupported view state type '$value'")
          }
        }
      }
      super.dispose()
    }

    private def cbViewSaveState: CheckMenuItem = {
      val Some(Menu.CheckBox(cb)) = window.handler.menuFactory.get(/* Some(window), */ "view.save-state")
      cb(window)
    }

    override protected def initGUI(): Unit = {
      val fileOpt = Try(new File(uri)).toOption
      windowFile  = fileOpt
      bindMenus(
        "view.save-state" -> Action(null) {
          val b = cbViewSaveState.selected
          Prefs.viewSaveState.put(b)
        }
      )
      if (saveViewState) {
        cbViewSaveState.selected = true
      }

      var MIN_BOUNDS_TIME = Long.MaxValue

      def updateBounds(e: UIElement): Unit = {
        val pt = e.bounds
        val b  = Vector(pt.x, pt.y, pt.width, pt.height)
        if (stateBounds != b) {
          stateBounds = b
          val vis     = System.currentTimeMillis() > MIN_BOUNDS_TIME //  e.visible
          // println(s"updateBounds: $stateBounds - $vis")
          if (vis) dirtyBounds = true
        }
      }

      if (!packAndPlace) {
        // pack()
        val Vec(x, y, w, h) = stateBounds
        window.component.peer.setBounds(x, y, w, h)
      }

      val rp  = window.component
      val r   = new Reactions.Impl
      rp.subscribe(r)
      r += {
        case UIElementMoved   (e) => updateBounds(e)
        case UIElementResized (e) => updateBounds(e)
        case UIElementShown   (_) => MIN_BOUNDS_TIME = System.currentTimeMillis() + 1000  // XXX TODO
      }
    }
  }
}