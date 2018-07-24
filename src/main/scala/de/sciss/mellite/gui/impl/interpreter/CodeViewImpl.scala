/*
 *  CodeViewImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
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
package interpreter

import java.awt.Color
import java.beans.{PropertyChangeEvent, PropertyChangeListener}

import javax.swing.Icon
import javax.swing.event.{UndoableEditEvent, UndoableEditListener}
import javax.swing.undo.UndoableEdit
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{KeyStrokes, UndoManager}
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Sys, TxnLike}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, defer, deferTx, requireEDT}
import de.sciss.model.impl.ModelImpl
import de.sciss.scalainterpreter.{CodePane, Interpreter, InterpreterPane, Style}
import de.sciss.swingplus.Implicits._
import de.sciss.swingplus.SpinningProgressBar
import de.sciss.syntaxpane.SyntaxDocument
import de.sciss.synth.proc.{Code, Workspace}

import scala.collection.immutable.{Seq => ISeq}
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.stm.Ref
import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, BorderPanel, Button, Component, FlowPanel}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object CodeViewImpl {
  private val intpMap = mutable.WeakHashMap.empty[Int, Future[Interpreter]]

  /* We use one shared interpreter for all code frames of each context. */
  private def interpreter(id: Int): Future[Interpreter] = {
    requireEDT()
    intpMap.getOrElse(id, {
      val cfg     = Interpreter.Config()
      cfg.imports = Code.getImports(id)
      val res     = Interpreter.async(cfg)
      intpMap.put(id, res)
      res
    })
  }

  def apply[S <: Sys[S]](obj: Code.Obj[S], code0: Code, bottom: ISeq[View[S]])
                        (handlerOpt: Option[CodeView.Handler[S, code0.In, code0.Out]])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         compiler: Code.Compiler,
                         undoManager: UndoManager): CodeView[S, code0.Out] = {

    val codeEx      = obj
    val codeVarHOpt = codeEx match {
      case Code.Obj.Var(vr) =>
        Some(tx.newHandle(vr))
      case _            => None
    }
    val res     = new Impl[S, code0.In, code0.Out](codeVarHOpt,
      code0, handlerOpt, bottom = bottom) // IntelliJ highlight bug
    res.init()
  }

  private final class Impl[S <: Sys[S], In0, Out0](codeVarHOpt: Option[stm.Source[S#Tx, Code.Obj.Var[S]]],
                                        private var code: Code { type In = In0; type Out = Out0 },
                                        handlerOpt: Option[CodeView.Handler[S, In0, Out0]],
                                        bottom: ISeq[View[S]])
                                       (implicit undoManager: UndoManager, val workspace: Workspace[S],
                                        val cursor: stm.Cursor[S], compiler: Code.Compiler)
    extends ComponentHolder[Component] with CodeView[S, Out0] with ModelImpl[CodeView.Update] {

    type C = Component

    private[this] val _dirty = Ref(false)

    def dirty(implicit tx: TxnLike): Boolean = _dirty.get(tx.peer)

    protected def dirty_=(value: Boolean): Unit = {
      requireEDT()
      val wasDirty = _dirty.single.swap(value)
      if (wasDirty != value) {
//        deferTx {
          actionApply.enabled = value
          dispatch(CodeView.DirtyChange(value))
//        }
      }
    }

//    private type CodeT = Code { type In = In0; type Out = Out0 }

    private def loadText(idx: Int): Unit = {
      try {
        val inp  = io.Source.fromFile(s"codeview$idx.txt", "UTF-8")
        val text = inp.getLines().mkString("\n")
        inp.close()
        codePane.editor.text = text
      } catch {
        case NonFatal(e) => e.printStackTrace()
      }
    }

    private[this] val codeCfg = {
      val b = CodePane.Config()
      // XXX TODO - should be a preferences option
      b.style = if (Mellite.isDarkSkin) Style.BlueForest else Style.Light
      b.text = code.source
      // XXX TODO - cheesy hack
      b.keyMap += (KeyStrokes.menu1 + Key.Key1 -> (() => loadText(1)))
      b.keyMap += (KeyStrokes.menu1 + Key.Key2 -> (() => loadText(2)))
      b.build
    }

    // import code.{id => codeId}

    private[this] var codePane: CodePane = _
    private[this] val futCompile = Ref(Option.empty[Future[Any]])
    private[this] var actionApply: Action = _

    def isCompiling(implicit tx: TxnLike): Boolean = futCompile.get(tx.peer).isDefined

    def currentText: String = codePane.editor.text

    def dispose()(implicit tx: S#Tx): Unit = ()

    def undoAction: Action = Action.wrap(codePane.editor.peer.getActionMap.get("undo"))
    def redoAction: Action = Action.wrap(codePane.editor.peer.getActionMap.get("redo"))

    private def saveSource(newSource: String)(implicit tx: S#Tx): Option[UndoableEdit] = {
      // val expr  = ExprImplicits[S]
      // import StringObj.{varSerializer, serializer}
      // val imp = ExprImplicits[S]
      codeVarHOpt.map { source =>
        val newCode = Code.Obj.newConst[S](code.updateSource(newSource))
        EditVar.Expr[S, Code, Code.Obj]("Change Source Code", source(), newCode)
      }
    }

    private def addEditAndClear(edit: UndoableEdit): Unit = {
      requireEDT()
      undoManager.add(edit)
      // this doesn't work properly
      // component.setDirty(value = false) // do not erase undo history

      // so let's clear the undo history now...
//      codePane.editor.peer.getDocument.asInstanceOf[SyntaxDocument].clearUndos()
    }

    def save(): Future[Unit] = {
      requireEDT()
      val newCode = currentText
      if (handlerOpt.isDefined) {
        compileSource(newCode, save = true, andThen = None)
      } else {
        val editOpt = cursor.step { implicit tx =>
          saveSource(newCode)
        }
        editOpt.foreach(addEditAndClear)
        Future.successful[Unit] {}
      }
    }

    private def saveSourceAndObject(newCode: String, in: In0, out: Out0)(implicit tx: S#Tx): Option[UndoableEdit] = {
      val edit1 = saveSource(newCode)
      val edit2 = handlerOpt.map { handler =>
        handler.save(in, out)
      }
      val edits0  = edit2.toList
      val edits   = edit1.fold(edits0)(_ :: edits0)
      CompoundEdit(edits, "Save and Apply Code")
    }

    private def compile(): Unit = compileSource(currentText, save = false, andThen = None)

    def preview(): Future[Out0] = {
      requireEDT()
      val p = Promise[Out0]()
      val newCode = currentText
      if (handlerOpt.isDefined) {
        compileSource(newCode, save = true, andThen = Some(p.success))
      } else {
        p.failure(new Exception("No handler defined"))
      }
      p.future
    }

    private def compileSource(newCode: String, save: Boolean, andThen: Option[Out0 => Unit]): Future[Unit] = {
      requireEDT()
      val saveObject = handlerOpt.isDefined && save
      if (futCompile.single.get.isDefined && !saveObject) return Future.successful[Unit] {}

      ggProgress                  .spinning = true
      actionCompile               .enabled  = false
      if (saveObject) actionApply .enabled  = false

      code = code.updateSource(newCode)

      val fut = handlerOpt match {
        case Some(handler) if save =>
          // val _fut = Library.compile(newCode)
          val _fut = Code.future {
            val in  = handler.in()
            val out = code.execute(in)
            (in, out)
          }
          _fut.foreach { case (in, out) =>
            defer {
              val editOpt = cursor.step { implicit tx =>
                saveSourceAndObject(newCode, in, out)
              }
              editOpt.foreach(addEditAndClear)
            }
          }
          _fut
        case _ =>
          code.compileBody()
      }

      futCompile.single.set(Some(fut))
      fut.onComplete { res =>
        futCompile.single.set(None)
        defer {
          // futCompile                    = None
          ggProgress          .spinning = false
          actionCompile       .enabled  = true
          if (saveObject) actionApply.enabled = true

          val iconColr = res match {
            case Success(_) =>
              clearGreen = true
              new Color(0x00, 0xC0, 0x00)                           // "\u2713"
            case Failure(Code.CompilationFailed()) => Color.red     // "error!"
            case Failure(Code.CodeIncomplete   ()) => Color.orange  // "incomplete!"
            case Failure(e) =>
              e.printStackTrace()
              Color.red
          }
          ggCompile.icon = compileIcon(Some(iconColr))
        }
      }
      fut.map(_ => ())
    }

    private[this] lazy val ggProgress = new SpinningProgressBar

    private[this] lazy val actionCompile = Action("Compile")(compile())

    private[this] lazy val ggCompile: Button = GUI.toolButton(actionCompile, raphael.Shapes.Hammer,
      tooltip = "Verify that current buffer compiles")

    private def compileIcon(colr: Option[Color]): Icon =
      raphael.Icon(extent = 20, fill = colr.getOrElse(raphael.TexturePaint(24)),
        shadow = raphael.WhiteShadow)(raphael.Shapes.Hammer)

    private[this] var clearGreen = false

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      this
    }

    private def guiInit(): Unit = {
      codePane            = CodePane(codeCfg)
      val iPane           = InterpreterPane.wrapAsync(interpreter(code.id), codePane)
      actionApply         = Action("Apply")(save())
      actionApply.enabled = false
      lazy val doc        = codePane.editor.peer.getDocument.asInstanceOf[SyntaxDocument]

      doc.addUndoableEditListener(
        new UndoableEditListener {
          def undoableEditHappened(e: UndoableEditEvent): Unit =
            if (clearGreen) {
              clearGreen = false
              ggCompile.icon = compileIcon(None)
            }
        }
      )

      doc.addPropertyChangeListener(SyntaxDocument.CAN_UNDO, new PropertyChangeListener {
        def propertyChange(e: PropertyChangeEvent): Unit = dirty = doc.canUndo
      })

      lazy val ggApply: Button = GUI.toolButton(actionApply, raphael.Shapes.Check , tooltip = "Save text changes")

      val bot0: List[Component] = ggProgress :: Nil
      val bot1 = if (bottom.isEmpty) bot0 else bot0 ++ bottom.map(_.component)
      val bot2 = HGlue :: ggApply :: ggCompile :: bot1
      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(bot2: _*)

      val iPaneC  = iPane.component
      val top     = iPaneC.peer.getComponent(iPaneC.peer.getComponentCount - 1) match {
        case jc: javax.swing.JComponent =>
          jc.add(panelBottom.peer)
          iPaneC
        case _ => new BorderPanel {
          add(iPaneC     , BorderPanel.Position.Center)
          add(panelBottom, BorderPanel.Position.South )
        }
      }

      component = top
      iPane.component.requestFocus()
    }
  }
}