/*
 *  ParamSpecObjView.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.audiowidgets.RotaryKnob
import de.sciss.desktop.{OptionPane, UndoManager}
import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, Expr, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.{ObjViewCmdLineParser, WorkspaceWindow}
import de.sciss.mellite.{GUI, ObjListView, ObjView, UniverseObjView, Veto, ViewState}
import de.sciss.model.impl.ModelImpl
import de.sciss.proc.Implicits._
import de.sciss.proc.{ParamSpec, Universe, Warp}
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.{ComboBox, GroupPanel, Spinner}
import de.sciss.{desktop, numbers}
import org.rogach.scallop

import java.text.NumberFormat
import java.util.Locale
import javax.swing.{DefaultBoundedRangeModel, Icon, SpinnerModel, SpinnerNumberModel}
import scala.concurrent.stm.Ref
import scala.concurrent.{Future, Promise}
import scala.swing.Swing.EmptyIcon
import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Action, Alignment, BorderPanel, BoxPanel, Component, Dialog, FlowPanel, Label, Orientation, Swing, TextField}
import scala.util.{Failure, Success}

object ParamSpecObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = ParamSpec.Obj[~]
  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Thermometer)
  val prefix        : String    = "ParamSpec"
  def humanName     : String    = "Param Spec"
  def tpe           : Obj.Type  = ParamSpec.Obj
  def category      : String    = ObjView.categOrganization
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: ParamSpec.Obj[T])(implicit tx: T): ParamSpecObjView[T] with ObjListView[T] = {
    val value     = obj.value
    val editable  = ParamSpec.Obj.Var.unapply(obj).isDefined
    new Impl(tx.newHandle(obj), value, isListCellEditable = editable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: ParamSpec = ParamSpec(),
                                        const: Boolean = false)

  private final class PanelImpl(nameIn: Option[String], editable: Boolean) extends ModelImpl[Unit] {

    private[this] val ggNameOpt = nameIn.map { txt0 => new TextField(txt0, 10) }
    private[this] val ggName    = ggNameOpt.getOrElse(Swing.HStrut(4))

    private[this] val mLo       = new SpinnerNumberModel(0.0, -1.0e6, 1.0e6, 0.1)
    private[this] val mHi       = new SpinnerNumberModel(1.0, -1.0e6, 1.0e6, 0.1)
    private[this] val mCurve    = new SpinnerNumberModel(0.0, -1.0e6, 1.0e6, 0.1)
    private[this] val sqWarp    = Seq[Warp](
      Warp.Linear, Warp.Exponential, Warp.Parametric(0d),
      Warp.Cosine, Warp.Sine, Warp.Fader, Warp.DbFader, Warp.Int
    )
    private[this] val nWarp     = sqWarp.map { w =>
      val n = w.toString // w.getClass.getSimpleName //.toLowerCase
      val i = n.indexOf("(")
      if (i < 0) n else n.substring(0, i)
    }

    private[this] val mWarp     = ComboBox.Model.wrap(nWarp)
    private[this] val ggUnit    = new TextField(4)

    def nameOption: Option[String] = ggNameOpt.flatMap { gg =>
      val s0 = gg.text
      if (s0.isEmpty) None else Some(s0)
    }

    def name: String = nameOption.getOrElse("")

    def name_=(value: String): Unit =
      ggNameOpt.foreach(_.text = value)

    def spec: ParamSpec = {
      val warpI = mWarp.selectedItem.fold(-1)(nWarp.indexOf)
      val warp0 = if (warpI < 0) Warp.Linear else sqWarp(warpI)
      val warp  = warp0 match {
        case p @ Warp.Parametric(_) => p.copy(curvature = mCurve.getNumber.doubleValue())
        case other => other
      }
      ParamSpec(
        lo = mLo.getNumber.doubleValue(), hi = mHi.getNumber.doubleValue(),
        warp = warp, unit = ggUnit.text
      )
    }

    def spec_=(value: ParamSpec): Unit = {
      mLo.setValue(value.lo)
      mHi.setValue(value.hi)
      val warpNorm = value.warp match {
        case p @ Warp.Parametric(c) =>
          mCurve.setValue(c)
          p.copy(curvature = 0.0)
        case other => other
      }
      val warpI = sqWarp.indexOf(warpNorm)
      mWarp.selectedItem = if (warpI < 0) None else Some(nWarp(warpI))
      ggUnit.text = value.unit

      updateExampleAndCurve()
    }

    private[this] val ggRotIn   = new RotaryKnob(new DefaultBoundedRangeModel(500, 0, 0, 1000))
    private[this] val ggRotOut  = new RotaryKnob(new DefaultBoundedRangeModel(500, 0, 0, 1000))
    private[this] var rotIsIn   = true
    private[this] var rotIsSet  = false
    private[this] val ggIn      = new TextField(8)
    private[this] val ggOut     = new TextField(8)
    ggOut.editable  = false
    ggIn .editable  = false

    private[this] val fmtDec  = NumberFormat.getNumberInstance(Locale.US)
    fmtDec.setMaximumFractionDigits(5)
    private[this] val fmtInt  = NumberFormat.getIntegerInstance(Locale.US)

    private def updateExample(fire: Boolean = true): ParamSpec = {
      rotIsSet = true
      try {
        if (rotIsIn) updateExampleFromIn (fire = fire)
        else         updateExampleFromOut(fire = fire)
      } finally {
        rotIsSet = false
      }
    }

    private def updateExampleFromIn(fire: Boolean): ParamSpec = {
      import numbers.Implicits._
      val spc     = spec
      val in      = ggRotIn.value * 0.001
      val out     = spc.map(in)
      ggRotOut.value = out.linLin(spc.lo, spc.hi, 0, 1000).toInt
      val inS     = fmtDec.format(in)
      val fmt     = if (spc.warp == Warp.Int) fmtInt else fmtDec
      val outS    = fmt.format(out)
      ggIn  .text = inS
      ggOut .text = outS
      if (fire) dispatch(())
      spc
    }

    private def updateExampleFromOut(fire: Boolean): ParamSpec = {
      import numbers.Implicits._
      val spc     = spec
      val out     = ggRotOut.value.linLin(0, 1000, spc.lo, spc.hi)
      val in      = spc.inverseMap(out)
      ggRotIn.value = (in * 1000).toInt
      val inS     = fmtDec.format(in)
      val fmt     = if (spc.warp == Warp.Int) fmtInt else fmtDec
      val outS    = fmt.format(out)
      ggIn  .text = inS
      ggOut .text = outS
      if (fire) dispatch(())
      spc
    }

    ggRotIn.listenTo(ggRotIn)
    ggRotIn.reactions += {
      case ValueChanged(_) if !rotIsSet =>
        rotIsIn = true
        updateExample()
    }

    ggRotOut.listenTo(ggRotOut)
    ggRotOut.reactions += {
      case ValueChanged(_) if !rotIsSet =>
        rotIsIn = false
        updateExample()
    }

    private def mkSpinner(m: SpinnerModel): Spinner = {
      val res = new Spinner(m)
      res.listenTo(res)
      res.reactions += {
        case ValueChanged(_) => updateExample()
      }
      res
    }

    val ggLo: Component = mkSpinner(mLo)
    val ggHi: Component = mkSpinner(mHi)

    private[this] val ggCurve   = mkSpinner(mCurve)
    private[this] val ggWarp    = new ComboBox(mWarp) {
      listenTo(selection)
      reactions += {
        case SelectionChanged(_) => updateExampleAndCurve()
      }
    }

    private[this] val lbName  = new Label(if (nameIn.isEmpty) "" else "Name:", EmptyIcon, Alignment.Right)
    private[this] val lbLo    = new Label( "Low Value:", EmptyIcon, Alignment.Right)
    private[this] val lbHi    = new Label("High Value:", EmptyIcon, Alignment.Right)
    private[this] val lbWarp  = new Label(      "Warp:", EmptyIcon, Alignment.Right)
    private[this] val lbUnit  = new Label(      "Unit:", EmptyIcon, Alignment.Right)
    private[this] val lbCurve = new Label( "Curvature:", EmptyIcon, Alignment.Right)

    private def updateExampleAndCurve(fire: Boolean = true): Unit = {
      val spc = updateExample(fire = false)
      ggCurve.enabled = editable && spc.warp.isInstanceOf[Warp.Parametric]
      if (fire) dispatch(())
    }

    updateExampleAndCurve(fire = false)

    private[this] val boxDemo = new FlowPanel(ggRotIn, new Label("In:"), ggIn, new Label("Out:"), ggOut, ggRotOut)

    private[this] val boxParams = new GroupPanel {
      horizontal= Seq(
        Par(Trailing)(lbName, lbLo  , lbHi   ), Par(ggName , ggLo  , ggHi   ),
        Gap.Preferred(GroupPanel.Placement.Unrelated),
        Par(Trailing)(lbUnit, lbWarp, lbCurve), Par(ggUnit , ggWarp, ggCurve))
      vertical = Seq(
        Par(Baseline)(lbName, ggName, lbUnit , ggUnit ),
        Par(Baseline)(lbLo  , ggLo  , lbWarp , ggWarp ),
        Par(Baseline)(lbHi  , ggHi  , lbCurve, ggCurve))
    }

    private[this] val box = new BoxPanel(Orientation.Vertical)
    box.contents += boxParams
    box.contents += Swing.VStrut(8)
    box.contents += boxDemo

    if (!editable) {
      ggLo    .enabled = false
      ggHi    .enabled = false
      ggWarp  .enabled = false
      ggCurve .enabled = false
    }

    def component: Component = box
  }

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val panel   = new PanelImpl(nameIn = Some(prefix), editable = true)
    panel.spec  = ParamSpec()

    val pane = desktop.OptionPane.confirmation(panel.component, optionType = Dialog.Options.OkCancel,
      messageType = Dialog.Message.Question, focus = Some(panel.ggHi))
    pane.title  = s"New $humanName"
    val res = pane.show(window)

    val res1 = if (res == Dialog.Result.Ok) {
      Success(Config[T](name = panel.name, value = panel.spec))
    } else {
      Failure(Aborted())
    }
    done(res1)
  }

  private val warpNameMap: Map[String, Warp] = Map(
    "lin"         -> Warp.Linear,
    "linear"      -> Warp.Linear,
    "exp"         -> Warp.Exponential,
    "exponential" -> Warp.Exponential,
    "cos"         -> Warp.Cosine,
    "cosine"      -> Warp.Cosine,
    "sin"         -> Warp.Sine,
    "sine"        -> Warp.Sine,
    "fader"       -> Warp.Fader,
    "dbfader"     -> Warp.DbFader,
    "int"         -> Warp.Int
  )

  private implicit val ReadWarp: scallop.ValueConverter[Warp] = scallop.singleArgConverter { s =>
    warpNameMap.getOrElse(s.toLowerCase(Locale.US), {
      val p = s.toDouble
      Warp.Parametric(p)
    })
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    val default: Config[T] = Config()
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val unit    : Opt[String] = opt(descr = "Unit label", default = Some(default.value.unit))
      val low     : Opt[Double] = trailArg(descr = "Lowest parameter value" , default = Some(default.value.lo))
      val high    : Opt[Double] = trailArg(descr = "Highest parameter value", default = Some(default.value.hi))
      val warp    : Opt[Warp  ] = trailArg(required = false,
        descr = s"Parameter warp or curve (default: ${"lin" /* default.value.warp */})",
        default = Some(Warp.Linear))
      val const   : Opt[Boolean] = opt    (descr = "Make constant instead of variable")
    }

    p.parse(Config(name = p.name(), const = p.const(), value = ParamSpec(
      lo = p.low(), hi = p.high(), warp = p.warp(), unit = p.unit())
    ))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = ParamSpec.Obj.newConst[T](value)
    val obj   = if (const) obj0 else ParamSpec.Obj.newVar(obj0)
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  private final class ViewImpl[T <: Txn[T]](objH: Source[T, ParamSpec.Obj[T]], val editable: Boolean)
                                           (implicit val universe: Universe[T],
                                            val undoManager: UndoManager)
    extends UniverseObjView[T] with View.Editable[T] with ComponentHolder[Component] {

    type C = Component

    override def obj(implicit tx: T): ParamSpec.Obj[T] = objH()

    override def viewState: Set[ViewState] = Set.empty

    private[this] var specValue   : ParamSpec         = _
    private[this] var panel       : PanelImpl         = _
    private[this] val _dirty      : Ref[Boolean]      = Ref(false)
    private[this] var actionApply : Action            = _
    private[this] var observer    : Disposable[T]  = _

    def init(spec0: ParamSpec.Obj[T])(implicit tx: T): this.type = {
      val spec0V = spec0.value
      deferTx(initGUI(spec0V))
      observer = spec0.changed.react { implicit tx => upd =>
        deferTx {
          specValue   = upd.now
          panel.spec  = upd.now
        }
      }
      this
    }

    private def initGUI(spec0: ParamSpec): Unit = {
      this.specValue = spec0
      panel = new PanelImpl(nameIn = None, editable = editable)
      panel.spec = spec0

      panel.addListener {
        case _ => updateDirty()
      }

      actionApply = Action("Apply") {
        save()
        updateDirty()
      }

      actionApply.enabled = false
      val ggApply = GUI.toolButton(actionApply, raphael.Shapes.Check, tooltip = "Save changes")
//      val panelBot = new BoxPanel(Orientation.Horizontal) {
//        contents += Swing.HGlue
//        contents += ggApply
//      }
      val panelBot = new FlowPanel(FlowPanel.Alignment.Trailing)(ggApply)

      component = new BorderPanel {
        add(panel.component, BorderPanel.Position.Center)
        add(panelBot       , BorderPanel.Position.South )
      }
    }

    def dirty(implicit tx: T): Boolean = _dirty.get(tx.peer)

    private def updateDirty(): Unit = {
      val specNowV = panel.spec
      val isDirty   = specValue != specNowV
      val wasDirty  = _dirty.single.swap(isDirty)
      if (isDirty != wasDirty) actionApply.enabled = isDirty
    }

    def save(): Unit = {
      requireEDT()
      val newSpec = panel.spec
      val editOpt = cursor.step { implicit tx =>
        objH() match {
          case ParamSpec.Obj.Var(pVr) =>
            val pVal  = ParamSpec.Obj.newConst[T](newSpec)
            val edit  = EditVar.Expr[T, ParamSpec, ParamSpec.Obj](s"Edit $humanName", pVr, pVal)
            Some(edit)
          case _ => None
        }
      }
      editOpt.foreach { edit =>
        undoManager.add(edit)
//        undoManager.clear()
        specValue = newSpec
      }
    }

    def dispose()(implicit tx: T): Unit = observer.dispose()
  }

  private final class FrameImpl[T <: Txn[T]](val view: ViewImpl[T],
                                             name: CellView[T, String])
    extends WorkspaceWindow[T](name) with Veto[T] {

    override protected def resizable: Boolean = false

    override def prepareDisposal()(implicit tx: T): Option[Veto[T]] =
      if (!view.editable || !view.dirty) None else Some(this)

    private def _vetoMessage = "The object has been edited."

    def vetoMessage(implicit tx: T): String = _vetoMessage

    def tryResolveVeto()(implicit tx: T): Future[Unit] = {
      val p = Promise[Unit]()
      deferTx {
        val message = s"${_vetoMessage}\nDo you want to save the changes?"
        val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
          messageType = OptionPane.Message.Warning)
        opt.title = s"Close - $title"
        opt.show(Some(window)) match {
          case OptionPane.Result.No =>
            p.success(())

          case OptionPane.Result.Yes =>
            /* val fut = */ view.save()
            p.success(())

          case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
            p.failure(Aborted())
        }
      }
      p.future
    }
  }

  private final class Impl[T <: Txn[T]](val objH: Source[T, ParamSpec.Obj[T]],
                                        var value: ParamSpec, val isListCellEditable: Boolean)
    extends ParamSpecObjView[T]
      with ObjListView[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.SimpleExpr[T, ParamSpec, ParamSpec.Obj]
      with ObjListViewImpl.StringRenderer { listObjView =>

    def factory: ObjView.Factory = ParamSpecObjView

    def exprType: Expr.Type[ParamSpec, ParamSpec.Obj] = ParamSpec.Obj

    def expr(implicit tx: T): ParamSpec.Obj[T] = obj

    def isViewable: Boolean = true

    def convertEditValue(v: Any): Option[ParamSpec] = None

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      implicit val undo: UndoManager = UndoManager()
      val _obj  = obj
      val view  = new ViewImpl[T](objH, editable = isListCellEditable).init(_obj)
      val nameView = CellView.name(_obj)
      val fr    = new FrameImpl[T](view, nameView).init()
      Some(fr)
    }
  }
}
trait ParamSpecObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = ParamSpec.Obj[T]
}