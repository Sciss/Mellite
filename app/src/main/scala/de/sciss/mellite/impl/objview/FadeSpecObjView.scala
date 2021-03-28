/*
 *  FadeSpecObjView.scala
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

import de.sciss.audiowidgets.{Axis, AxisFormat, ParamField, TimeField}
import de.sciss.desktop
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.desktop.{OptionPane, UndoManager}
import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.swing.LucreSwing.{deferTx, requireEDT}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Disposable, DoubleObj, LongObj, Obj, Source, Txn => LTxn}
import de.sciss.mellite.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.impl.{ObjViewCmdLineParser, RenderingImpl, WorkspaceWindow}
import de.sciss.mellite.{GUI, ObjListView, ObjView, Shapes, UniverseObjView, Veto, ViewState}
import de.sciss.model.impl.ModelImpl
import de.sciss.numbers.Implicits.doubleNumberWrapper
import de.sciss.proc.Implicits._
import de.sciss.proc.{CurveObj, FadeSpec, TimeRef, Universe}
import de.sciss.processor.Processor.Aborted
import de.sciss.span.Span
import de.sciss.swingplus.{ComboBox, GroupPanel, Spinner}
import de.sciss.synth.Curve

import java.awt.image.ImageObserver
import javax.swing.undo.UndoableEdit
import javax.swing.{Icon, SpinnerModel, SpinnerNumberModel}
import scala.concurrent.stm.Ref
import scala.concurrent.{Future, Promise}
import scala.swing.Swing.EmptyIcon
import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Action, Alignment, BorderPanel, BoxPanel, Component, Dialog, Dimension, FlowPanel, Graphics2D, Label, Orientation, Swing, TextField}
import scala.util.{Failure, Success}

object FadeSpecObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = FadeSpec.Obj[~]
  val icon          : Icon      = raphaelIcon(Shapes.Aperture)
  val prefix        : String    = "FadeSpec"
  val humanName     : String    = "Fade Spec"
  def tpe           : Obj.Type  = FadeSpec.Obj
  def category      : String    = ObjView.categMisc
  def canMakeObj    : Boolean   = true

  private def DefaultValue = FadeSpec(TimeRef.SampleRate.toLong)

  final case class Config[T <: LTxn[T]](name: String = prefix,
                                        value: FadeSpec = DefaultValue, const: Boolean = false)

  def mkListView[T <: Txn[T]](obj: FadeSpec.Obj[T])(implicit tx: T): ObjListView[T] = {
    val value     = obj.value
    val editable  = obj match {
      case FadeSpec.Obj.Var(_) => true
      case FadeSpec.Obj(LongObj.Var(_), CurveObj.Var(_), DoubleObj.Var(_)) => true
      case _ => false
    }
    new Impl[T](tx.newHandle(obj), value, editable = editable).init(obj)
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    import CmdLineSupport._
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val duration: Opt[Frames] = trailArg[Frames](
        descr = "Duration (e.g. '1411200' frames, '100 ms' milliseconds, '0:00.100')"
      )

      val curve: Opt[Curve] = trailArg(required = false,
        descr = s"Curve (default: ${"lin" /* default.value.curve */})",
        default = Some(Curve.linear))

      val floor: Opt[Double] = opt(
        descr = s"Floor level in decibels"
      )

      val const: Opt[Boolean] = opt(descr = "Make constant instead of variable")
    }

    p.parse(Config(name = p.name(), const = p.const(), value = (p.duration(), p.curve(), p.floor.toOption) match {
      case (Frames(numFr), curve, floorOpt) =>
        val floor = floorOpt.fold(curve match {
          case Curve.exponential => -80.0.dbAmp
          case _                 => 0.0
        })(_.dbAmp)
        FadeSpec(numFr, curve, floor.toFloat)
    }))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = FadeSpec.Obj.newConst[T](value)
    val obj   = if (const) obj0 else FadeSpec.Obj.newVar(obj0)
    if (name.nonEmpty) obj.name = name
    obj :: Nil
  }

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val panel   = new PanelImpl(nameIn = Some(prefix), editable = true)
    panel.spec  = DefaultValue

    val pane = desktop.OptionPane.confirmation(panel.component, optionType = Dialog.Options.OkCancel,
      messageType = Dialog.Message.Question, focus = Some(panel.ggCurve))
    pane.title  = s"New $humanName"
    val res = pane.show(window)

    val res1 = if (res == Dialog.Result.Ok) {
      Success(Config[T](name = panel.name, value = panel.spec))
    } else {
      Failure(Aborted())
    }
    done(res1)
  }

  private val timeFmt = AxisFormat.Time(hours = false, millis = true)

  private final class PanelImpl(nameIn: Option[String], editable: Boolean)
    extends RenderingImpl(GUI.isDarkSkin) with ModelImpl[Unit] {

    def imageObserver: ImageObserver = component.peer

    private[this] val ggNameOpt = nameIn.map { txt0 => new TextField(txt0, 10) }
    private[this] val ggName    = ggNameOpt.getOrElse(Swing.HStrut(4))

    private[this] val ggNumFrames: ParamField[Long] = {
      val res = new TimeField(value0 = 0L, span0 = Span.From(0L),
        sampleRate = TimeRef.SampleRate, viewSampleRate0 = 0.0,
        clipStart = true, clipStop = false)
      res.listenTo(res)
      res.reactions += {
        case ValueChanged(_) => updateView()
      }
      res
    }

    private[this] val mFloor    = new SpinnerNumberModel(0.0, Double.NegativeInfinity, 0.0, 0.1)
    private[this] val ggFloor   = mkSpinner(mFloor)

    private[this] val mCurvature  = new SpinnerNumberModel(0.0, -1.0e6, 1.0e6, 0.1)
    private[this] val sqCurve     = Seq[Curve](
      Curve.step, Curve.linear, Curve.exponential, Curve.sine,
      Curve.welch, Curve.parametric(0f), Curve.squared, Curve.cubed
    )
    private[this] val nCurve     = sqCurve.map {
      case Curve.parametric(_)  => "parametric"
      case c                    =>  c.toString
    }

    private[this] val mCurve    = ComboBox.Model.wrap(nCurve)

    private final class ViewShape(in: Boolean) extends Component {
      preferredSize = new Dimension(240, 360)
      minimumSize   = new Dimension( 24,  36)

      override protected def paintComponent(g: Graphics2D): Unit = {
        super.paintComponent(g)
        val spc = spec
        val w   = peer.getWidth
        val h   = peer.getHeight
        val y1  = if (in) spc.floor else 1f
        val y2  = if (in) 1f else spc.floor
        val x0  = if (in) 0 else w
        paintFade(g, spc.curve, fw = w, pyi = 0, phi = h, y1 = y1, y2 = y2, x = 0, x0 = x0)
      }
    }

    private[this] val cViewShapeIn  : Component = new ViewShape(in = true )
    private[this] val cViewShapeOut : Component = new ViewShape(in = false)

    private[this] val viewYAxis = {
      val a = new Axis(Orientation.Vertical)
      a.minimum = -60
      a.maximum = 0.0
      a.format  = AxisFormat.Decimal
      val d     = a.preferredSize
      d.height  = cViewShapeIn.preferredSize.height
      a.preferredSize = d
      a
    }

    private[this] val viewXAxisIn = {
      val a = new Axis(Orientation.Horizontal)
      a.format  = timeFmt
      a
    }

    private[this] val viewXAxisOut = {
      val a = new Axis(Orientation.Horizontal)
      a.format  = timeFmt
      a
    }

    def nameOption: Option[String] = ggNameOpt.flatMap { gg =>
      val s0 = gg.text
      if (s0.isEmpty) None else Some(s0)
    }

    def name: String = nameOption.getOrElse("")

    def name_=(value: String): Unit =
      ggNameOpt.foreach(_.text = value)

    private def curve: Curve = {
      val curveI = mCurve.selectedItem.fold(-1)(nCurve.indexOf)
      val curve0 = if (curveI < 0) Curve.linear else sqCurve(curveI)
      curve0 match {
        case p @ Curve.parametric(_) => p.copy(curvature = mCurvature.getNumber.floatValue())
        case other => other
      }
    }

    def spec: FadeSpec = {
      val _curve  = curve
      val floor0 = mFloor.getNumber.doubleValue()
      val floor   = if (floor0 == -160.0) 0f else floor0.dbAmp.toFloat

      FadeSpec(
        numFrames = ggNumFrames.value,
        curve     = _curve,
        floor     = floor,
      )
    }

    def spec_=(value: FadeSpec): Unit = {
      ggNumFrames.value = value.numFrames
      val curveNorm = value.curve match {
        case p @ Curve.parametric(c) =>
          mCurvature.setValue(c)
          p.copy(curvature = 0.0f)
        case other => other
      }
      val curveI = sqCurve.indexOf(curveNorm)
      mCurve.selectedItem = if (curveI < 0) None else Some(nCurve(curveI))

      val floorDb = value.floor.ampDb // .clip(-160.0, 0.0)
      mFloor.setValue(floorDb)

      updateExampleAndCurve()
    }

    private def updateView(fire: Boolean = true): FadeSpec = {
      val spc = spec
      val dur = spc.numFrames / TimeRef.SampleRate
      viewXAxisIn .maximum = dur
      viewXAxisOut.maximum = dur
      cViewShapeIn .repaint()
      cViewShapeOut.repaint()
      if (fire) dispatch(())
      spc
    }

    private def mkSpinner(m: SpinnerModel): Spinner = {
      val res = new Spinner(m)
      res.listenTo(res)
      res.reactions += {
        case ValueChanged(_) => updateView()
      }
      res
    }

    private[this] val ggCurvature = mkSpinner(mCurvature)
    val ggCurve: ComboBox[String] = new ComboBox(mCurve) {
      listenTo(selection)
      reactions += {
        case SelectionChanged(_) =>
          if (selection.item == Curve.exp.toString()) {
            if (mFloor.getNumber.doubleValue() == Double.NegativeInfinity) mFloor.setValue(-80.0)
          } else {
            if (mFloor.getNumber.doubleValue() == -80.0) mFloor.setValue(Double.NegativeInfinity)
          }
          updateExampleAndCurve()
      }
    }

    private[this] val lbName      = new Label(if (nameIn.isEmpty) "" else "Name:", EmptyIcon, Alignment.Right)
    private[this] val lbNumFrames = new Label(  "Duration:", EmptyIcon, Alignment.Right)
    private[this] val lbCurve     = new Label(     "Curve:", EmptyIcon, Alignment.Right)
    private[this] val lbCurvature = new Label( "Curvature:", EmptyIcon, Alignment.Right)
    private[this] val lbFloor     = new Label("Floor [dB]:", EmptyIcon, Alignment.Right)

    private def updateExampleAndCurve(fire: Boolean = true): Unit = {
      val spc = updateView(fire = false)
      ggCurvature.enabled = editable && (spc.curve match {
        case  Curve.parametric(_) => true
        case _                    => false
      })
      if (fire) dispatch(())
    }

    updateExampleAndCurve(fire = false)

    private[this] val boxParams = new GroupPanel {
      private val glue = Swing.VGlue
      horizontal= Par(
        Seq(
          Par(Trailing)(lbName, lbNumFrames, lbCurve, lbCurvature, lbFloor),
          Par(ggName, ggNumFrames, ggCurve, ggCurvature, ggFloor),
        ),
        glue,
      )
      vertical = Seq(
        Par(Baseline)(lbName      , ggName      ),
        Par(Baseline)(lbNumFrames , ggNumFrames ),
        Par(Baseline)(lbCurve     , ggCurve     ),
        Par(Baseline)(lbCurvature , ggCurvature ),
        Par(Baseline)(lbFloor     , ggFloor     ),
        glue,
      )
    }

    private[this] val box = new BoxPanel(Orientation.Horizontal)
    box.contents += boxParams
    box.contents += Swing.HStrut(16)
    box.contents += new BoxPanel(Orientation.Vertical) {
      contents += Swing.VStrut(viewXAxisIn.preferredSize.height)
      contents += viewYAxis
    }
    box.contents += new BoxPanel(Orientation.Vertical) {
      contents += viewXAxisIn
      contents += cViewShapeIn
    }
    box.contents += Swing.HStrut(2)
    box.contents += new BoxPanel(Orientation.Vertical) {
      contents += viewXAxisOut
      contents += cViewShapeOut
    }

    if (!editable) {
      ggNumFrames .enabled = false
      ggCurve     .enabled = false
      ggCurvature .enabled = false
      ggFloor     .enabled = false
    }

    def component: Component = box
  }

  private final class ViewImpl[T <: Txn[T]](objH: Source[T, FadeSpec.Obj[T]], val editable: Boolean)
                                           (implicit val universe: Universe[T],
                                            val undoManager: UndoManager)
    extends UniverseObjView[T] with View.Editable[T] with ComponentHolder[Component] {

    type C = Component

    override def obj(implicit tx: T): FadeSpec.Obj[T] = objH()

    override def viewState: Set[ViewState] = Set.empty

    private[this] var specValue   : FadeSpec          = _
    private[this] var panel       : PanelImpl         = _
    private[this] val _dirty      : Ref[Boolean]      = Ref(false)
    private[this] var actionApply : Action            = _
    private[this] var observer    : Disposable[T]  = _

    def init(spec0: FadeSpec.Obj[T])(implicit tx: T): this.type = {
      val spec0V = spec0.value
      deferTx(guiInit(spec0V))
      observer = spec0.changed.react { implicit tx => upd =>
        deferTx {
          specValue   = upd.now
          panel.spec  = upd.now
        }
      }
      this
    }

    private def guiInit(spec0: FadeSpec): Unit = {
      this.specValue  = spec0
      panel           = new PanelImpl(nameIn = None, editable = editable)
      panel.spec      = spec0

      panel.addListener {
        case _ => updateDirty()
      }

      actionApply = Action("Apply") {
        save()
        updateDirty()
      }

      actionApply.enabled = false
      val ggApply = GUI.toolButton(actionApply, raphael.Shapes.Check, tooltip = "Save changes")
      val panelBot = new FlowPanel(FlowPanel.Alignment.Trailing)(ggApply)

      component = new BorderPanel {
        add(panel.component, BorderPanel.Position.Center)
        add(panelBot       , BorderPanel.Position.South )
      }
    }

    def dirty(implicit tx: T): Boolean = _dirty.get(tx.peer)

    private def updateDirty(): Unit = {
      val specNowV  = panel.spec
      val isDirty   = specValue != specNowV
      val wasDirty  = _dirty.single.swap(isDirty)
      if (isDirty != wasDirty) actionApply.enabled = isDirty
    }

    def save(): Unit = {
      requireEDT()
      val newSpec = panel.spec
      val editOpt = cursor.step { implicit tx =>
        val editName = s"Edit $humanName"
        objH() match {
          case FadeSpec.Obj.Var(vr) =>
            val value = FadeSpec.Obj.newConst[T](newSpec)
            val edit  = EditVar.Expr[T, FadeSpec, FadeSpec.Obj](editName, vr, value)
            Some(edit)

          case FadeSpec.Obj(LongObj.Var(vrNumFr), CurveObj.Var(vrCurve), DoubleObj.Var(vrFloor)) =>
            var edits   = List.empty[UndoableEdit]
            if (newSpec.numFrames != specValue.numFrames) {
              val vNumFr  = LongObj.newConst[T](newSpec.numFrames)
              val edNumFr = EditVar.Expr[T, Long, LongObj]("Set Num Frames", vrNumFr, vNumFr)
              edits ::= edNumFr
            }
            if (newSpec.curve != specValue.curve) {
              val vCurve  = CurveObj.newConst[T](newSpec.curve)
              val edCurve = EditVar.Expr[T, Curve, CurveObj]("Set Curve", vrCurve, vCurve)
              edits ::= edCurve
            }
            if (newSpec.floor != specValue.floor) {
              val vFloor  = DoubleObj.newConst[T](newSpec.floor)
              val edFloor = EditVar.Expr[T, Double, DoubleObj]("Set Floor", vrFloor, vFloor)
              edits ::= edFloor
            }
            CompoundEdit(edits, editName)

          case _ => None
        }
      }
      editOpt.foreach { edit =>
        undoManager.add(edit)
        specValue = newSpec
      }
    }

    def dispose()(implicit tx: T): Unit = observer.dispose()
  }

  // XXX TODO DRY with ParamSpecObjView
  private final class FrameImpl[T <: Txn[T]](val view: ViewImpl[T],
                                             name: CellView[T, String])
    extends WorkspaceWindow[T](name) with Veto[T] {

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
        (opt.show(Some(window)): @unchecked) match {
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

  // XXX TODO DRY with ParamSpecObjView
  final class Impl[T <: Txn[T]](val objH: Source[T, FadeSpec.Obj[T]], var value: FadeSpec, editable: Boolean)
    extends ObjListView /* .FadeSpec */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.NonEditable[T] {

    type Repr = FadeSpec.Obj[T]

    def factory: ObjView.Factory = FadeSpecObjView

    def init(obj: FadeSpec.Obj[T])(implicit tx: T): this.type = {
      initAttrs(obj)
      addDisposable(obj.changed.react { implicit tx =>upd =>
        deferAndRepaint {
          value = upd.now
        }
      })
      this
    }

    def isViewable: Boolean = true

    override def openView(parent: Option[Window[T]])(implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      implicit val undo: UndoManager = UndoManager()
      val _obj  = obj
      val view  = new ViewImpl[T](objH, editable = editable /*isListCellEditable*/).init(_obj)
      val nameView = CellView.name(_obj)
      val fr    = new FrameImpl[T](view, nameView).init()
      Some(fr)
    }

    def configureListCellRenderer(label: Label): Component = {
      val sr      = TimeRef.SampleRate // 44100.0
      val dur     = timeFmt.format(value.numFrames.toDouble / sr)
      label.text  = s"$dur, ${value.curve}"
      label
    }
  }
}
