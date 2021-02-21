/*
 *  InterpreterFrameImpl.scala
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

package de.sciss.mellite.impl.code

import de.sciss.desktop
import de.sciss.desktop.{KeyStrokes, Window, WindowHandler}
import de.sciss.mellite.{Application, GUI, InterpreterFrame}
import de.sciss.proc.Workspace
import de.sciss.scalainterpreter.{CodePane, Interpreter, InterpreterPane, Style}

import java.io.{File, FileInputStream, IOException}
import scala.swing.event.Key

private[mellite] object InterpreterFrameImpl {
  private def readFile(file: File): String = {
    val fis = new FileInputStream(file)
    try {
      val arr = new Array[Byte](fis.available())
      fis.read(arr)
      new String(arr, "UTF-8")
    } finally {
      fis.close()
    }
  }

  def apply(): InterpreterFrame = {
    val codeCfg = CodePane.Config()
    // XXX TODO - should be a preferences option
    codeCfg.style = if (GUI.isDarkSkin) Style.BlueForest else Style.Light

    val file = new File(/* new File( "" ).getAbsoluteFile.getParentFile, */ "interpreter.txt")
    if (file.isFile) try {
      codeCfg.text = readFile(file)
    } catch {
      case e: IOException => e.printStackTrace()
    }

    val txnKeyStroke  = KeyStrokes.shift + KeyStrokes.alt + Key.Enter
    // var txnCount      = 0

    def txnExecute(): Unit = {
      val codePane = intp.codePane
      codePane.activeRange.foreach { range =>
        // val txnId = txnCount
        // txnCount += 1
        Application.documentHandler.activeDocument.foreach {
          case _: Workspace.Confluent =>
                        //            val txnTxt =
            //              s"""class _txnBody$txnId(implicit t: scala.concurrent.stm.InTxn) {
            //             |import MelliteDSL._
            //             |$txt
            //             |}
            //             |val _txnRes$txnId = doc.cursor.atomic(implicit t => new _txnBody$txnId)
            //             |import _txnRes$txnId._""".stripMargin
            val txt = codePane.getTextSlice(range)
            val txnTxt =
              s"""confluentDocument.cursors.cursor.step { implicit tx =>
             |import de.sciss.mellite.InterpreterFrame.Bindings.{confluentDocument => doc}
             |val _imp = proc.ExprImplicits[proc.Confluent]
             |import _imp._
             |$txt
             |}""".stripMargin

            codePane.flash(range)
            val res  = intp.interpret(txnTxt)
            val succ = res.exists(_.isSuccess)
            if (!succ) codePane.abortFlash()
            res
          case _ =>
        }
      }
    }

    codeCfg.keyMap += txnKeyStroke -> (() => txnExecute())

    lazy val intpCfg = Interpreter.Config()
    intpCfg.imports = List(
      "de.sciss.mellite._",
      "de.sciss.synth._",
      "de.sciss.proc",
      "de.sciss.synth.Ops._",
      // "concurrent.duration._",
      "de.sciss.proc.Implicits._",
      "de.sciss.span.Span",
      // "MelliteDSL._",
      "InterpreterFrame.Bindings._"
    )

    //      intpCfg.bindings = Seq( NamedParam( "replSupport", replSupport ))
    //         in.bind( "s", classOf[ Server ].getName, ntp )
    //         in.bind( "in", classOf[ Interpreter ].getName, in )

    //      intpCfg.out = Some( LogWindow.instance.log.writer )

    lazy val intp = InterpreterPane(interpreterConfig = intpCfg, codePaneConfig = codeCfg)

    new InterpreterFrame {
      val component: desktop.Window = new de.sciss.desktop.impl.WindowImpl {
        frame =>

        def handler: WindowHandler = Application.windowHandler

        // override def style = Window.Auxiliary

        title           = "Interpreter"
        contents        = intp.component
        closeOperation  = Window.CloseDispose
        pack()
        desktop.Util.centerOnScreen(this)
        front()
      }
    }
  }
}
