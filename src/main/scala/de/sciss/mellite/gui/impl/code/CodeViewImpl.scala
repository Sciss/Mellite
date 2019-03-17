/*
 *  CodeViewImpl2.scala
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
package gui
package impl
package code

import java.awt.Color

import de.sciss.desktop.{KeyStrokes, UndoManager, Util}
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Sys, TxnLike}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, defer, deferTx, requireEDT}
import de.sciss.model.impl.ModelImpl
import de.sciss.scalainterpreter.Interpreter
import de.sciss.swingplus.SpinningProgressBar
import de.sciss.synth.proc.{Code, Universe}
import dotterweide.Span
import dotterweide.build.Version
import dotterweide.editor.controller.FlashAction
import dotterweide.editor.painter.FlashPainter
import dotterweide.editor.{ColorScheme, Editor, Flash, FlashImpl}
import dotterweide.ide.ActionAdapter
import dotterweide.languages.scala.ScalaLanguage
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.collection.mutable
import scala.concurrent.stm.Ref
import scala.concurrent.{Future, Promise}
import scala.swing.Swing._
import scala.swing.event.Key
import scala.swing.{Action, Button, Component, FlowPanel}
import scala.util.{Failure, Success, Try}

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
                        (implicit tx: S#Tx, universe: Universe[S],
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
                                                  (implicit undoManager: UndoManager, val universe: Universe[S],
                                                   compiler: Code.Compiler)
    extends ComponentHolder[Component] with CodeView[S, Out0] with ModelImpl[CodeView.Update] {

    type C = Component

    private[this] val _dirty = Ref(false)

    private[this] val language = {
      val v = de.sciss.mellite.BuildInfo.scalaVersion
      new ScalaLanguage(
        scalaVersion      = Version.parse(v).get,
        prelude           = Code.fullPrelude(code),
        postlude          = code.postlude,
        examples          = Nil
      )
    }

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

    private[this] var editorPanel: dotterweide.ide.Panel = _
    private[this] val futCompile = Ref(Option.empty[Future[Any]])
    private[this] var actionApply: Action = _

    def isCompiling(implicit tx: TxnLike): Boolean =
      futCompile.get(tx.peer).isDefined

    def currentText: String = editorPanel.data.text

    def dispose()(implicit tx: S#Tx): Unit = {
      bottom.foreach(_.dispose())
      deferTx {
        editorPanel.dispose()
      }
    }

    def undoAction: Action = new ActionAdapter(editorPanel.currentEditor.actions.undo)
    def redoAction: Action = new ActionAdapter(editorPanel.currentEditor.actions.redo)

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

      // so let's clear the undo history now...
      // (note that if we don't do this, the user will see
      // a warning dialog when closing the window)
      editorPanel.history.clear()
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

    private[this] lazy val ggCompile: Button = {
      val ks  = KeyStrokes.menu1 + Key.F9
      val res = GUI.toolButton(actionCompile, raphael.Shapes.Hammer,
        tooltip = s"Verify that buffer compiles (${GUI.keyStrokeText(ks)})")
      Util.addGlobalKey(res, ks)
      res
    }

    private def compileIcon(colr: Option[Color]): Icon =
      raphael.Icon(extent = 20, fill = colr.getOrElse(raphael.TexturePaint(24)),
        shadow = raphael.WhiteShadow)(raphael.Shapes.Hammer)

    private[this] var clearGreen = false

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      this
    }

    private class InterpreterFlash(ed: Editor, intp: Interpreter, flash: Flash)
      extends FlashAction(ed.document, ed.terminal, flash) {

      override protected def run(id: Int, span: Span): Unit = {
        val res = intp.interpret(span.text)
        if (!res.isSuccess) flash.changeLevel(id, Flash.LevelError)
      }
    }

    private def intpReady(tr: Try[Interpreter]): Unit = tr match {
      case Success(intp) =>
        val flash = new FlashImpl
        editorPanel.editors.foreach { ed =>
          val action  = new InterpreterFlash(ed, intp, flash)
          val painter = new FlashPainter(ed.painterContext, flash)
          ed.addAction  (action)
          ed.addPainter (painter)
        }

      case Failure(ex) =>
        ex.printStackTrace()
        val msg = "Failed to initialize interpreter!"
        editorPanel.status.message = msg
    }

    private def guiInit(): Unit = {
//      val scalaVersionP = "version.number"
//      val scalaVersion  = scala.util.Properties.scalaPropOrNone(scalaVersionP).flatMap(Version.parse(_).toOption)
//        .getOrElse(throw new NoSuchElementException(scalaVersionP))
//      println(prelude)
//      println(code.postlude)
      editorPanel          = new dotterweide.ide.PanelImpl(
        language          = language,
        text0             = code.source,
//        font        = ...,
        stylingName       = Some(if (Mellite.isDarkSkin) ColorScheme.DarkName else ColorScheme.LightName),
        preferredGridSize = Some((24, 68))
      )
      val intpFut       = interpreter(code.tpe.id)
      intpFut.value match {
        case Some(tr) =>
          intpReady(tr)
        case None =>
          intpFut.onComplete { tr =>
            defer {
              intpReady(tr)
            }
          }
      }

      actionApply         = Action("Apply")(save())
      actionApply.enabled = false

      // codePane.structureVisible = true  // debug

      editorPanel.editors.foreach { ed =>
        val action = ApiBrowser.lookUpDocAction(code, ed, language)
        ed.addAction(action)
      }

      editorPanel.document.onChange { _ =>
        if (clearGreen) {
          clearGreen = false
          ggCompile.icon = compileIcon(None)
        }
      }

      editorPanel.onChange {
        case dotterweide.ide.Panel.DirtyChanged(d) => dirty = d
        case _ =>
      }

      val ksApply = KeyStrokes.menu1 + Key.S
      val ggApply = GUI.toolButton(actionApply, raphael.Shapes.Check,
        tooltip = s"Save changes (${GUI.keyStrokeText(ksApply)})")
      Util.addGlobalKey(ggApply, ksApply)

      val bot0: List[Component] = ggProgress :: Nil
      val bot1 = if (bottom.isEmpty) bot0 else bot0 ++ bottom.map(_.component)
      val bot2 = HGlue :: ggApply :: ggCompile :: bot1
      val panelBottom = new FlowPanel(FlowPanel.Alignment.Trailing)(bot2: _*)

      val iPaneC  = editorPanel.component
      editorPanel.setBottomRightComponent(panelBottom)

      component = iPaneC
      iPaneC.requestFocus()
    }
  }
}