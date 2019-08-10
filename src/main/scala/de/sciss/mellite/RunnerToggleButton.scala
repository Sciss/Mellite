 /*
 *  RunnerToggleButton.scala
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

import de.sciss.desktop.{KeyStrokes, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.synth.proc.{Runner, Universe}

import scala.swing.ToggleButton
import scala.swing.event.{ButtonClicked, Key}

object RunnerToggleButton {
//  def apply[S <: Sys[S]](transport: Transport[S])(implicit tx: S#Tx): PlayToggleButton[S] =
//    new Impl[S](transport, objH = None, disposeTransport = false).init()

  /** A button view toggling between `run` and `stop` an a runner created from the `obj` argument. */
  def apply[S <: SSys[S]](obj: Obj[S])(implicit tx: S#Tx, universe: Universe[S]): RunnerToggleButton[S] = {
    val r = Runner(obj)
    new Impl[S](r, disposeRunner = true).init()
  }

  private final class Impl[S <: Sys[S]](val runner: Runner[S], disposeRunner: Boolean)
    extends RunnerToggleButton[S] with ComponentHolder[ToggleButton] {

    private[this] var obs: Disposable[S#Tx] = _

    def dispose()(implicit tx: S#Tx): Unit = {
      obs.dispose()
      if (disposeRunner) runner.dispose()
    }

    private def select(selected: Boolean)(implicit tx: S#Tx): Unit =
      deferTx {
        component.selected = selected
      }

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      obs = runner.react { implicit tx => state =>
        select(selected = !state.idle)
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
            /*SoundProcesses.atomic[S, Unit]*/ { implicit tx =>
              runner.stop()
              if (sel) {
                runner.run()
              }
            } // (transport.scheduler.cursor)
        }
      }
      val shpPower          = raphael.Shapes.Power _
      ggPower.icon          = GUI.iconNormal  (shpPower)
      ggPower.disabledIcon  = GUI.iconDisabled(shpPower)
      val ksPower           = KeyStrokes.shift + Key.F10
      ggPower.tooltip       = s"Toggle DSP (${GUI.keyStrokeText(ksPower)})"
      Util.addGlobalKey(ggPower, ksPower)
      component             = ggPower
    }
  }
}
trait RunnerToggleButton[S <: Sys[S]] extends View[S] {
  type C = ToggleButton

//  def transport: Transport[S]
}