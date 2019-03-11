/*
 *  CodeFrameImpl.scala
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

import de.sciss.desktop.{OptionPane, UndoManager, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{IdPeek, Obj}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.ProcActions
import de.sciss.mellite.gui.impl.WindowImpl
import de.sciss.mellite.gui.{ActionBounce, AttrMapView, CanBounce, CodeFrame, CodeView, GUI, PlayToggleButton, ProcOutputsView, SplitPaneView}
import de.sciss.mellite.util.Veto
import de.sciss.processor.Processor.Aborted
import de.sciss.synth.SynthGraph
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.impl.ActionImpl
import de.sciss.synth.proc.{Action, Code, Proc, SynthGraphObj, Universe}
import javax.swing.undo.UndoableEdit

import scala.collection.immutable.{Seq => ISeq}
import scala.concurrent.{Future, Promise}
import scala.swing.{Button, Component, Orientation, TabbedPane}

object CodeFrameImpl {
  // ---- adapter for editing a Proc's source ----

  def proc[S <: Sys[S]](obj: Proc[S])
                       (implicit tx: S#Tx, universe: Universe[S],
                        compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = mkSource(obj = obj, codeId = Code.SynthGraph.id, key = Proc.attrSource,
      init = {
        val txt     = ProcActions.extractSource(obj.graph.value)
        val comment = if (txt.isEmpty)
            "SoundProcesses graph function source code"
          else
            "source code automatically extracted"
        s"// $comment\n\n$txt"
      })

    val codeEx0 = codeObj
    val objH    = tx.newHandle(obj)
    val code0   = codeEx0.value match {
      case cs: Code.SynthGraph => cs
      case other => sys.error(s"Proc source code does not produce SynthGraph: ${other.contextName}")
    }

    val handler = new CodeView.Handler[S, Unit, SynthGraph] {
      def in(): Unit = ()

      def save(in: Unit, out: SynthGraph)(implicit tx: S#Tx): UndoableEdit = {
        val obj = objH()
        import universe.cursor
        EditVar.Expr[S, SynthGraph, SynthGraphObj]("Change SynthGraph", obj.graph, SynthGraphObj.newConst[S](out))
      }

      def dispose()(implicit tx: S#Tx): Unit = ()
    }

    implicit val undo: UndoManager = UndoManager()
    val outputsView = ProcOutputsView [S](obj)
    val attrView    = AttrMapView     [S](obj)
    val viewPower   = PlayToggleButton[S](obj)
    val rightView   = SplitPaneView(attrView, outputsView, Orientation.Vertical)

    make(obj, objH, codeObj, code0, Some(handler), bottom = viewPower :: Nil,
      rightViewOpt = Some(("In/Out", rightView)), canBounce = true)
  }

  // ---- adapter for editing a Action's source ----

  def action[S <: Sys[S]](obj: Action[S])
                         (implicit tx: S#Tx, universe: Universe[S],
                          compiler: Code.Compiler): CodeFrame[S] = {
    val codeObj = mkSource(obj = obj, codeId = Code.Action.id, key = Action.attrSource,
      init = "// Action source code\n\n")

    val codeEx0 = codeObj
    val code0   = codeEx0.value match {
      case cs: Code.Action => cs
      case other => sys.error(s"Action source code does not produce plain function: ${other.contextName}")
    }

    val objH  = tx.newHandle(obj)
    val viewExecute = View.wrap[S, Button] {
      val actionExecute = swing.Action(null) {
        import universe.cursor
        cursor.step { implicit tx =>
          val obj = objH()
          val au = Action.Universe(obj)
          obj.execute(au)
        }
      }
      GUI.toolButton(actionExecute, raphael.Shapes.Bolt, tooltip = "Run body")
    }

    val handlerOpt = obj match {
      case Action.Var(vr) =>
        val varH  = tx.newHandle(vr)
        val handler = new CodeView.Handler[S, String, Array[Byte]] {
          def in(): String = {
            import universe.cursor
            cursor.step { implicit tx =>
              val id = tx.newId()
              val cnt = IdPeek(id)
              s"Action$cnt"
            }
          }

          def save(in: String, out: Array[Byte])(implicit tx: S#Tx): UndoableEdit = {
            val obj = varH()
            val value = ActionImpl.newConst[S](name = in, jar = out)
            import universe.cursor
            EditVar[S, Action[S], Action.Var[S]](name = "Change Action Body", expr = obj, value = value)
          }

          def dispose()(implicit tx: S#Tx): Unit = ()
        }

        Some(handler)

      case _ => None
    }

    val bottom = viewExecute :: Nil

    implicit val undo: UndoManager = UndoManager()
    make(obj, objH, codeObj, code0, handlerOpt, bottom = bottom, rightViewOpt = None, canBounce = false)
  }

  // ---- general constructor ----

  def apply[S <: Sys[S]](obj: Code.Obj[S], bottom: ISeq[View[S]], canBounce: Boolean = false)
                        (implicit tx: S#Tx, universe: Universe[S],
                         compiler: Code.Compiler): CodeFrame[S] = {
    val _codeEx = obj

    val _code: CodeT[_, _] = _codeEx.value    // IntelliJ highlight bug
    implicit val undo: UndoManager = UndoManager()
    val objH    = tx.newHandle(obj)

    make[S, _code.In, _code.Out](pObj = obj, pObjH = objH, obj = obj, code0 = _code, handler = None,
      bottom = bottom, rightViewOpt = None, canBounce = canBounce)
  }

  private class PlainView[S <: Sys[S]](codeView: View[S], rightViewOpt: Option[(String, View[S])])
                                      (implicit val universe: Universe[S],
                                       val undoManager: UndoManager)
    extends View.Editable[S] with UniverseView[S] with ComponentHolder[Component] {

    type C = Component

//    private[this] var tabs: TabbedPane  = _

    def init()(implicit tx: S#Tx): this.type = {
      deferTx(guiInit())
      this
    }

    private def guiInit(): Unit = {
      component = rightViewOpt.fold[C](codeView.component) { case (rightTitle, rightView) =>
        val _tabs = new TabbedPane
        _tabs.peer.putClientProperty("styleId", "attached")
        _tabs.focusable  = false
        val pageEdit    = new TabbedPane.Page("Editor"  , codeView  .component, null)
        val pageRender  = new TabbedPane.Page(rightTitle, rightView .component, null)
        _tabs.pages     += pageEdit
        _tabs.pages     += pageRender
        //      _tabs.pages     += pageAttr
        Util.addTabNavigation(_tabs)

        //      render(initialText)

        // tabs = _tabs

//        val res = new SplitPane(Orientation.Vertical, codeView.component, rightView.component)
//        res.oneTouchExpandable  = true
//        res.resizeWeight        = 1.0
//        // cf. https://stackoverflow.com/questions/4934499
//        res.peer.addAncestorListener(new AncestorListener {
//          def ancestorAdded  (e: AncestorEvent): Unit = res.dividerLocation = 1.0
//          def ancestorRemoved(e: AncestorEvent): Unit = ()
//          def ancestorMoved  (e: AncestorEvent): Unit = ()
//        })
//        res

        _tabs
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = {
      codeView                 .dispose()
      rightViewOpt.foreach(_._2.dispose())
    }
  }

  private final class CanBounceView[S <: Sys[S]](objH: stm.Source[S#Tx, Obj[S]], codeView: View[S],
                                                 rightViewOpt: Option[(String, View[S])])
                                                (implicit universe: Universe[S],
                                                 undoManager: UndoManager)
    extends PlainView[S](codeView, rightViewOpt) with CanBounce {

    object actionBounce extends ActionBounce[S](this, objH)
  }

  // trying to minimize IntelliJ false error highlights
  private final type CodeT[In0, Out0] = Code { type In = In0; type Out = Out0 }

  /**
    * @param rightViewOpt optional title and component for a second tab view
    */
  def make[S <: Sys[S], In0, Out0](pObj: Obj[S], pObjH: stm.Source[S#Tx, Obj[S]], obj: Code.Obj[S],
                                   code0: CodeT[In0, Out0],
                                   handler: Option[CodeView.Handler[S, In0, Out0]], bottom: ISeq[View[S]],
                                   rightViewOpt: Option[(String, View[S])], canBounce: Boolean)
                                  (implicit tx: S#Tx, universe: Universe[S],
                                   undoManager: UndoManager, compiler: Code.Compiler): CodeFrame[S] = {
    // val _name   = /* title getOrElse */ obj.attr.name
    val codeView  = CodeView(obj, code0, bottom = bottom)(handler)

    val view      = if (canBounce)
      new CanBounceView(pObjH, codeView, rightViewOpt)
    else
      new PlainView(codeView, rightViewOpt)

    view.init()
    val _name = CellView.name(pObj)
    val res = new FrameImpl(codeView = codeView, view = view, name = _name, contextName = code0.contextName)
    res.init()
    res
  }

  // ---- util ----

  def mkSource[S <: Sys[S]](obj: Obj[S], codeId: Int, key: String, init: => String)
                                   (implicit tx: S#Tx): Code.Obj[S] = {
    // if there is no source code attached,
    // create a new code object and add it to the attribute map.
    // let's just do that without undo manager
    val codeObj = obj.attr.get(key) match {
      case Some(c: Code.Obj[S]) => c
      case _ =>
        val source  = init
        val code    = Code(codeId, source)
        val c       = Code.Obj.newVar(Code.Obj.newConst[S](code))
        obj.attr.put(key, c)
        c
    }
    codeObj
  }

  // ---- frame impl ----

  private final class FrameImpl[S <: Sys[S]](val codeView: CodeView[S, _], val view: View[S],
                                             name: CellView[S#Tx, String], contextName: String)
    extends WindowImpl[S](name.map(n => s"$n : $contextName Code")) with CodeFrame[S] with Veto[S#Tx] {

    override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
      if (!codeView.isCompiling && !codeView.dirty) None else Some(this)

    private def _vetoMessage = "The code has been edited."

    def vetoMessage(implicit tx: S#Tx): String =
      if (codeView.isCompiling) "Ongoing compilation." else _vetoMessage

    def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] =
      if (codeView.isCompiling) Future.failed(Aborted())
      else {
        val p = Promise[Unit]()
        deferTx {
          val message = "The code has been edited.\nDo you want to save the changes?"
          val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
            messageType = OptionPane.Message.Warning)
          opt.title = s"Close - $title"
          val res = opt.show(Some(window))
          res match {
            case OptionPane.Result.No =>
              p.success(())
            case OptionPane.Result.Yes =>
              /* val fut = */ codeView.save()
              p.success(())

            case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
              p.failure(Aborted())
          }
        }
        p.future
      }
  }
}