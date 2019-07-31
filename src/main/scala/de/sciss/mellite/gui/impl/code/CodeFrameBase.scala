/*
 *  CodeFrameBase.scala
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

package de.sciss.mellite.gui.impl.code

import de.sciss.desktop.{Menu, OptionPane}
import de.sciss.kollflitz.ISeq
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.mellite.{CodeView, Veto}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.proc.Code.Example

import scala.concurrent.{Future, Promise}

/** Building block for code frames that vetoes closing when
  * code is not saved or compilation not finished.
  *
  * Also provides menu generation for examples.
  */
trait CodeFrameBase[S <: Sys[S]] extends Veto[S#Tx] {
  _: WindowImpl[S] =>

  protected def codeView: CodeView[S, _]

  override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
    if (!codeView.isCompiling && !codeView.dirty) None else Some(this)

  private def _vetoMessage = "The code has been edited."

  private class ExampleAction(ex: Example) extends swing.Action(ex.name) {
    if (ex.mnemonic != 0) mnemonic = ex.mnemonic

    def apply(): Unit =
      codeView.currentText = ex.code
  }

  def vetoMessage(implicit tx: S#Tx): String =
    if (codeView.isCompiling) "Ongoing compilation." else _vetoMessage

  def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] =
    if (codeView.isCompiling) Future.failed(Aborted())
    else {
      val p = Promise[Unit]()
      deferTx {
        val message = s"${_vetoMessage}\nDo you want to save the changes?"
        val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
          messageType = OptionPane.Message.Warning)
        opt.title = s"Close - $title"
        val res = opt.show(Some(window))
        res match {
          case OptionPane.Result.No =>
            p.success(())
          case OptionPane.Result.Yes =>
            val fut = codeView.save()
            // XXX TODO --- what was the reason we didn't wait for completion?
//            p.success(())
            p.completeWith(fut)

          case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
            p.failure(Aborted())
        }
      }
      p.future
    }

  protected final def mkExamplesMenu(examples: ISeq[Example]): Unit =
    if (examples.nonEmpty) {
      val gEx = Menu.Group("examples", "Examples")
      gEx.action.mnemonic = 'x'
      examples.iterator.zipWithIndex.foreach { case (ex, i) =>
        val aEx = new ExampleAction(ex)
        gEx.add(Menu.Item(s"example-$i", aEx))
      }
      val mf = window.handler.menuFactory
      mf.add(Some(window), gEx)
    }
}
