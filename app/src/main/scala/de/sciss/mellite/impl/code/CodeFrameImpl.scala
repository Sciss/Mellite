/*
 *  CodeFrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite.impl.code

import java.awt.event.{ComponentAdapter, ComponentEvent, ComponentListener}

import de.sciss.desktop.{Menu, UndoManager, Util}
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.View
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{BooleanObj, Obj, Source}
import de.sciss.mellite.impl.WindowImpl
import de.sciss.mellite.{ActionBounce, AttrMapView, CanBounce, CodeFrame, CodeView, ProcActions, ProcOutputsView, RunnerToggleButton, SplitPaneView, UniverseView}
import de.sciss.synth.SynthGraph
import de.sciss.proc.Code.Example
import de.sciss.proc.{Action, Code, Control, Proc, Universe, Widget}
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.swing.{BorderPanel, Component, FlowPanel, Orientation, TabbedPane}
import scala.util.control.NonFatal

object CodeFrameImpl extends CodeFrame.Companion {
  def install(): Unit =
    CodeFrame.peer = this

  // ---- adapter for editing a Proc's source ----

  def proc[T <: Txn[T]](obj: Proc[T])
                       (implicit tx: T, universe: Universe[T],
                        compiler: Code.Compiler): CodeFrame[T] = {
    val codeObj = mkSource(obj = obj, codeTpe = Code.Proc, key = Proc.attrSource)({
      val gv: SynthGraph = obj.graph.value
      val txt     = /*if (gv.isEmpty) "" else*/ try {
        ProcActions.extractSource(gv)
      } catch {
        case NonFatal(ex) =>
          s"// $ex"
      }
      if (txt.isEmpty)
        Code.Proc.defaultSource
      else
        s"// source code automatically extracted\n\n$txt"
    })

    val objH    = tx.newHandle(obj)
    val code0   = codeObj.value match {
      case cs: Code.Proc => cs
      case other => sys.error(s"Proc source code does not produce SynthGraph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[T, Unit, SynthGraph] {
      def in(): Unit = ()

      def save(in: Unit, out: SynthGraph)(implicit tx: T): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[T, SynthGraph, Proc.GraphObj]("Change SynthGraph", obj.graph, Proc.GraphObj.newConst[T](out))
      }

      def dispose()(implicit tx: T): Unit = ()
    }

    implicit val undo: UndoManager = UndoManager()
    val outputsView = ProcOutputsView [T](obj)
    val attrView    = AttrMapView     [T](obj)
    val viewPower   = RunnerToggleButton[T](obj)
    val rightView   = SplitPaneView(attrView, outputsView, Orientation.Vertical)

    make(obj, objH, codeObj, code0, Some(handler), bottom = viewPower :: Nil,
      rightViewOpt = Some(("In/Out", rightView)), canBounce = true,
//      alwaysShowBottom = true
    )
  }

  // ---- adapter for editing a Control.Graph's source ----

  def control[T <: Txn[T]](obj: Control[T])
                          (implicit tx: T, universe: Universe[T],
                           compiler: Code.Compiler): CodeFrame[T] = {
    val codeObj = mkSource(obj = obj, codeTpe = Code.Control, key = Control.attrSource)({
      val gv: Control.Graph = obj.graph.value
      if (gv.controls.isEmpty)
        Code.Control.defaultSource
      else
        s"// Warning: source code could not be automatically extracted!\n\n"
    })

    val objH    = tx.newHandle(obj)
    val code0   = codeObj.value match {
      case cs: Code.Control => cs
      case other => sys.error(s"Control source code does not produce Control.Graph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[T, Unit, Control.Graph] {
      def in(): Unit = ()

      def save(in: Unit, out: Control.Graph)(implicit tx: T): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[T, Control.Graph, Control.GraphObj]("Change Control Graph", obj.graph,
          Control.GraphObj.newConst[T](out))
      }

      def dispose()(implicit tx: T): Unit = ()
    }

    implicit val undo: UndoManager = UndoManager()
    val attrView    = AttrMapView     [T](obj)
    val viewPower   = RunnerToggleButton[T](obj)
    val rightView   = attrView // SplitPaneView(attrView, outputsView, Orientation.Vertical)

    make(obj, objH, codeObj, code0, Some(handler), bottom = viewPower :: Nil,
      rightViewOpt = Some(("In/Out", rightView)), canBounce = true,
//      alwaysShowBottom = true
    )
  }

  // ---- adapter for editing a Control.Graph's source ----

  def action[T <: Txn[T]](obj: Action[T])
                          (implicit tx: T, universe: Universe[T],
                           compiler: Code.Compiler): CodeFrame[T] = {
    val codeObj = mkSource(obj = obj, codeTpe = Code.Action, key = Action.attrSource)({
      val gv: Action.Graph = obj.graph.value
      if (gv.controls.isEmpty)
        Code.Action.defaultSource
      else
        s"// Warning: source code could not be automatically extracted!\n\n"
    })

    val objH    = tx.newHandle(obj)
    val code0   = codeObj.value match {
      case cs: Code.Action => cs
      case other => sys.error(s"Action source code does not produce Action.Graph: ${other.tpe.humanName}")
    }

    val handler = new CodeView.Handler[T, Unit, Action.Graph] {
      def in(): Unit = ()

      def save(in: Unit, out: Action.Graph)(implicit tx: T): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[T, Action.Graph, Action.GraphObj]("Change Action Graph", obj.graph,
          Action.GraphObj.newConst[T](out))
      }

      def dispose()(implicit tx: T): Unit = ()
    }

    implicit val undo: UndoManager = UndoManager()
    val attrView    = AttrMapView[T](obj)
    val viewPower   = RunnerToggleButton[T](obj, isAction = true)
    val rightView   = attrView // SplitPaneView(attrView, outputsView, Orientation.Vertical)

    make(obj, objH, codeObj, code0, Some(handler), bottom = viewPower :: Nil,
      rightViewOpt = Some(("In/Out", rightView)), canBounce = true,
//      alwaysShowBottom = true
    )
  }

  // ---- general constructor ----

  def apply[T <: Txn[T]](obj: Code.Obj[T], bottom: ISeq[View[T]])
                        (implicit tx: T, universe: Universe[T],
                         compiler: Code.Compiler): CodeFrame[T] = {
    apply(obj, bottom, canBounce = false)
  }

  def apply[T <: Txn[T]](obj: Code.Obj[T], bottom: ISeq[View[T]], canBounce: Boolean)
                        (implicit tx: T, universe: Universe[T],
                         compiler: Code.Compiler): CodeFrame[T] = {
    val _codeEx = obj

    val _code: CodeT[_, _] = _codeEx.value    // IntelliJ highlight bug
    implicit val undo: UndoManager = UndoManager()
    val objH    = tx.newHandle(obj)

    make[T, _code.In, _code.Out](pObj = obj, pObjH = objH, obj = obj, code0 = _code, handler = None,
      bottom = bottom, rightViewOpt = None, debugMenuItems = Nil, canBounce = canBounce)
  }

  private class PlainView[T <: Txn[T]](codeView: View[T], rightViewOpt: Option[(String, View[T])],
                                       showEditor: Boolean, bottom: ISeq[View[T]])
                                      (implicit val universe: Universe[T],
                                       val undoManager: UndoManager)
    extends View.Editable[T] with UniverseView[T] with ComponentHolder[Component] {

    type C = Component

//    private[this] var tabs: TabbedPane  = _

    def init()(implicit tx: T): this.type = {
      deferTx(guiInit())
      this
    }

    private def guiInit(): Unit = {
      val pane = rightViewOpt.fold[C](codeView.component) { case (rightTitle, rightView) =>
        val _tabs = new TabbedPane
        _tabs.peer.putClientProperty("styleId", "attached")  // XXX TODO: obsolete
        _tabs.focusable  = false
        val pageEditC   = if (showEditor) codeView.component else {
          new BorderPanel {
            private def addEditor(): Unit =
              add(codeView.component, BorderPanel.Position.Center)

            lazy val cl: ComponentListener = new ComponentAdapter {
              override def componentShown(e: ComponentEvent): Unit = {
                if (peer.isShowing) {
                  peer.removeComponentListener(cl)
                  addEditor()
                  revalidate()
                }
              }
            }
            peer.addComponentListener(cl)
          }
        }

        val pageEdit    = new TabbedPane.Page("Editor"  , pageEditC, null)
        val pageRender  = new TabbedPane.Page(rightTitle, rightView .component, null)
        _tabs.pages     += pageEdit
        _tabs.pages     += pageRender
        Util.addTabNavigation(_tabs)

        if (showEditor) {
          codeView.component.requestFocus()
        } else {
          _tabs.selection.index = 1
          // paneEdit.preferredSize = renderer.component.preferredSize
        }

        _tabs
      }

      component = if (bottom.isEmpty) pane else new BorderPanel {
        add(pane, BorderPanel.Position.Center)

        {
          val botC          = bottom.map(_.component)
          val panelBottom   = new FlowPanel(FlowPanel.Alignment.Trailing)(botC: _*)
          panelBottom.hGap  = 4
          panelBottom.vGap  = 2
          add(panelBottom, BorderPanel.Position.South)
        }
      }
    }

    def dispose()(implicit tx: T): Unit = {
      codeView.dispose()
      rightViewOpt.foreach(_._2.dispose())
      bottom.foreach(_.dispose())
    }
  }

  private final class CanBounceView[T <: Txn[T]](objH: Source[T, Obj[T]], codeView: View[T],
                                                 rightViewOpt: Option[(String, View[T])],
                                                 showEditor: Boolean, bottom: ISeq[View[T]])
                                                (implicit universe: Universe[T],
                                                 undoManager: UndoManager)
    extends PlainView[T](codeView, rightViewOpt, showEditor = showEditor, bottom = bottom) with CanBounce {

    object actionBounce extends ActionBounce[T](this, objH)
  }

  // trying to minimize IntelliJ false error highlights
  private final type CodeT[In0, Out0] = Code { type In = In0; type Out = Out0 }

  /**
    * @param rightViewOpt optional title and component for a second tab view
    */
  def make[T <: Txn[T], In0, Out0](pObj           : Obj[T],
                                   pObjH          : Source[T, Obj[T]],
                                   obj            : Code.Obj[T],
                                   code0          : CodeT[In0, Out0],
                                   handler        : Option[CodeView.Handler[T, In0, Out0]],
                                   bottom         : ISeq[View[T]],
//                                   alwaysShowBottom: Boolean,
                                   rightViewOpt   : Option[(String, View[T])] = None,
                                   debugMenuItems : ISeq[swing.Action]        = Nil,
                                   canBounce      : Boolean
                                  )
                                  (implicit tx: T, universe: Universe[T],
                                   undoManager: UndoManager, compiler: Code.Compiler): CodeFrame[T] = {
    val showEditor  = pObj.attr.$[BooleanObj](Widget.attrEditMode).forall(_.value)
    val bottomCode  = if (showEditor) bottom else Nil
    val bottomView  = if (showEditor) Nil else bottom
    val codeView    = CodeView(obj, code0, bottom = bottomCode)(handler)

    val view = if (canBounce)
      new CanBounceView(pObjH,  codeView, rightViewOpt, showEditor = showEditor, bottom = bottomView)
    else
      new PlainView(            codeView, rightViewOpt, showEditor = showEditor, bottom = bottomView)

    view.init()
    val _name = CellView.name(pObj)
    val res = new FrameImpl(codeView = codeView, view = view, name = _name, contextName = code0.tpe.humanName,
      debugMenuItems = debugMenuItems, examples = code0.tpe.examples
    )
    res.init()
    res
  }

  // ---- util ----

  def mkSource[T <: Txn[T]](obj: Obj[T], codeTpe: Code.Type, key: String)(init: => String = codeTpe.defaultSource)
                           (implicit tx: T): Code.Obj[T] = {
    // if there is no source code attached,
    // create a new code object and add it to the attribute map.
    // let's just do that without undo manager
    val codeObj = obj.attr.get(key) match {
      case Some(c: Code.Obj[T]) => c
      case _ =>
        val source  = init
        val code    = Code(codeTpe.id, source)
        val c       = Code.Obj.newVar(Code.Obj.newConst[T](code))
        obj.attr.put(key, c)
        c
    }
    codeObj
  }

  // ---- frame impl ----

  private final class FrameImpl[T <: Txn[T]](val codeView   : CodeView[T, _],
                                             val view       : View[T],
                                             name           : CellView[T, String],
                                             contextName    : String,
                                             debugMenuItems : ISeq[swing.Action],
                                             examples       : ISeq[Example]
                                            )
    extends WindowImpl[T](name.map(n => s"$n : $contextName Code"))
      with CodeFrameBase[T]
      with CodeFrame[T] {

    override protected def initGUI(): Unit = {
      super.initGUI()
      if (debugMenuItems.nonEmpty) {
        val mf = window.handler.menuFactory
        mf.get("actions").foreach {
          case g: Menu.Group =>
            val winOpt = Some(window)
            debugMenuItems.iterator.zipWithIndex.foreach { case (a, ai) =>
              g.add(winOpt, Menu.Item(s"debug-${ai + 1}", a))
            }
          case _ =>
        }
      }
      mkExamplesMenu(examples)
    }
  }
}