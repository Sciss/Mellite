 /*
 *  PlayToggleButton.scala
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

package de.sciss.mellite.gui

import de.sciss.desktop.{KeyStrokes, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, Sys}
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.synth.proc.{SoundProcesses, Transport, Universe}

import scala.concurrent.stm.Ref
import scala.swing.ToggleButton
import scala.swing.event.{ButtonClicked, Key}

object PlayToggleButton {
  def apply[S <: Sys[S]](transport: Transport[S])(implicit tx: S#Tx): PlayToggleButton[S] =
    new Impl[S](transport, objH = None, disposeTransport = false).init()

  def apply[S <: SSys[S]](obj: Obj[S])(implicit tx: S#Tx, universe: Universe[S]): PlayToggleButton[S] = {
    val t = Transport[S](universe)
    // We are not simply adding the object here, because if the aural system
    // is running, this will create an aural object, which in turn may throw
    // an exception (this is not yet handled well), for example in SysSon
    // when a Proc contains sonification specific objects that are not supported
    // by the default AuralProc.
    // Instead we add the object dynamically when the transport is started.
//    t.addObject(obj)
    new Impl[S](t, objH = Some(tx.newHandle(obj)), disposeTransport = true).init()
  }

  private final class Impl[S <: Sys[S]](val transport: Transport[S], objH: Option[stm.Source[S#Tx, Obj[S]]],
                                        disposeTransport: Boolean)
    extends PlayToggleButton[S] with ComponentHolder[ToggleButton] {

    private[this] var obs: Disposable[S#Tx] = _
    private[this] val added = Ref(false)

    def dispose()(implicit tx: S#Tx): Unit = {
      obs.dispose()
      if (disposeTransport) transport.dispose()
    }

    private def select(state: Boolean)(implicit tx: S#Tx): Unit =
      deferTx {
        component.selected = state
      }

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      obs = transport.react { implicit tx => {
        case Transport.Play(_, _) => select(state = true )
        case Transport.Stop(_, _) => select(state = false)
        case _ =>
      }}
      this
    }

    private def guiInit(): Unit = {
      val ggPower = new ToggleButton {
        listenTo(this)
        reactions += {
          case ButtonClicked(_) =>
            val sel = selected
            SoundProcesses.atomic[S, Unit] { implicit tx =>
              transport.stop()
              if (added.swap(false)(tx.peer)) objH.foreach(h => transport.removeObject(h()))
              transport.seek(0L)
              if (sel) {
                objH.foreach { h =>
                  transport.addObject(h())
                  added.set(true)(tx.peer)
                }
                transport.play()
              }
            } (transport.scheduler.cursor)
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
trait PlayToggleButton[S <: Sys[S]] extends View[S] {
  type C = ToggleButton

  def transport: Transport[S]
}