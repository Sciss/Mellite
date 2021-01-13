 /*
 *  RunnerToggleButton.scala
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

package de.sciss.mellite

import de.sciss.desktop.{KeyStrokes, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.{Disposable, Obj, Txn, synth}
import de.sciss.proc.Runner.{Done, Failed, State}
import de.sciss.proc.{Runner, Universe}

import scala.swing.ToggleButton
import scala.swing.event.{ButtonClicked, Key}

object RunnerToggleButton {
  /** A button view toggling between `run` and `stop` an a runner created from the `obj` argument. */
  def apply[T <: synth.Txn[T]](obj: Obj[T], isAction: Boolean = false)
                         (implicit tx: T, universe: Universe[T]): RunnerToggleButton[T] = {
    val r = Runner(obj)
    new Impl[T](r, disposeRunner = true, isAction = isAction).init()
  }

  private final class Impl[T <: Txn[T]](val runner: Runner[T], disposeRunner: Boolean, isAction: Boolean)
    extends RunnerToggleButton[T] with ComponentHolder[ToggleButton] {

    private[this] var obs: Disposable[T] = _

    def dispose()(implicit tx: T): Unit = {
      obs.dispose()
      if (disposeRunner) runner.dispose()
    }

    private[this] val shapeFun = if (isAction) raphael.Shapes.Bolt _ else raphael.Shapes.Power _

    private[this] lazy val icnNormal  = GUI.iconNormal  (shapeFun)
    private[this] lazy val icnDone    = GUI.iconSuccess (shapeFun)
    private[this] lazy val icnFailed  = GUI.iconFailure (shapeFun)

    private def select(state: State)(implicit tx: T): Unit = {
      val selected = !state.idle
      deferTx {
        val c = component
        c.selected = selected
        c.icon = state match {
          // we don't use green indicator with immediately terminating actions
          // to avoid visual noise
          case Done if !isAction  => icnDone
          case Failed(_)          => icnFailed
          case _                  => icnNormal
        }
      }
    }

    def init()(implicit tx: T): this.type = {
      deferTx(guiInit())
      obs = runner.react { implicit tx => state =>
        select(state)
        state match {
          case Failed(ex) =>
            ex.printStackTrace()
          case _ =>
        }
      }
      this
    }

    private def guiInit(): Unit = {
      val ggPower = new ToggleButton {
        listenTo(this)
        reactions += {
          case ButtonClicked(_) =>
            val sel = selected
            val sch = runner.universe.scheduler
            sch.stepTag
            /*SoundProcesses.atomic[T, Unit]*/ { implicit tx =>
              runner.stop()
              if (sel) {
                runner.run()
              }
            } // (transport.scheduler.cursor)
        }
      }
      ggPower.icon          = icnNormal
      ggPower.disabledIcon  = GUI.iconDisabled(shapeFun)
      val ksPower           = KeyStrokes.shift + Key.F10
      ggPower.tooltip       = s"Toggle DSP (${GUI.keyStrokeText(ksPower)})"
      Util.addGlobalKey(ggPower, ksPower)
      component             = ggPower
    }
  }
}
trait RunnerToggleButton[T <: Txn[T]] extends View[T] {
  type C = ToggleButton

//  def transport: Transport[T]
}