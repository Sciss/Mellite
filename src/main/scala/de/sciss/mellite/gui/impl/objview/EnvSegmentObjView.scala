/*
 *  EnvSegmentObjView.scala
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

package de.sciss.mellite.gui.impl.objview

import java.awt.geom.Area

import de.sciss.desktop
import de.sciss.desktop.UndoManager
import de.sciss.desktop.edit.CompoundEdit
import de.sciss.equal.Implicits._
import de.sciss.icons.raphael
import de.sciss.kollflitz.Vec
import de.sciss.lucre.expr.{CellView, DoubleObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window, deferTx, requireEDT}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.GraphemeObjView.{HandleDiameter, HandleRadius, HasStartLevels}
import de.sciss.mellite.gui.impl.grapheme.GraphemeObjViewImpl
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.raphaelIcon
import de.sciss.mellite.gui.impl.{ObjViewCmdLineParser, WindowImpl}
import de.sciss.mellite.gui.{GraphemeObjView, GraphemeRendering, GraphemeView, Insets, ListObjView, ObjView}
import de.sciss.model.impl.ModelImpl
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.{ComboBox, GroupPanel, Spinner}
import de.sciss.synth.Curve
import de.sciss.synth.proc.Grapheme.Entry
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{CurveObj, EnvSegment, Universe}
import javax.swing.undo.UndoableEdit
import javax.swing.{Icon, SpinnerModel, SpinnerNumberModel}

import scala.swing.Swing.EmptyIcon
import scala.swing.event.{SelectionChanged, ValueChanged}
import scala.swing.{Alignment, Component, Dialog, Graphics2D, Label, Swing, TextField}
import scala.util.{Failure, Success}

object EnvSegmentObjView extends ListObjView.Factory with GraphemeObjView.Factory {
  type E[S <: stm.Sys[S]]       = EnvSegment.Obj[S]
  type V                        = EnvSegment
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Connect)
  val prefix        : String    = "EnvSegment"
  def humanName     : String    = "Envelope Segment"
  def tpe           : Obj.Type  = EnvSegment.Obj
  def category      : String    = ObjView.categPrimitives
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: E[S])(implicit tx: S#Tx): ListObjView[S] = {
    val value     = obj.value
    val editable  = EnvSegment.Obj.Var.unapply(obj).isDefined
    new ListImpl[S](tx.newHandle(obj), value = value, isEditable = editable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String       = prefix,
                                           value: EnvSegment  = EnvSegment.Single(0.0, Curve.lin),
                                           const: Boolean     = false)

  // XXX TODO DRY with ParamSpecObjView
  private final class PanelImpl(nameIn: Option[String], editable: Boolean) extends ModelImpl[Unit] {

    private[this] val ggNameOpt = nameIn.map { txt0 => new TextField(txt0, 10) }
    private[this] val ggName    = ggNameOpt.getOrElse(Swing.HStrut(4))

    private[this] val mStartLvl = new SpinnerNumberModel(0.0, -1.0e6, 1.0e6, 0.1)
    private[this] val mParam    = new SpinnerNumberModel(0.0, -1.0e6, 1.0e6, 0.1)
    private[this] val sqCurve   = Seq[Curve](
      Curve.step, Curve.linear, Curve.exponential,
      Curve.sine, Curve.welch, Curve.parametric(0f), Curve.squared, Curve.cubed
    )
    private[this] val nCurve    = sqCurve.map { w => w.toString.capitalize }
    private[this] val mCurve    = ComboBox.Model.wrap(nCurve)

    def nameOption: Option[String] = ggNameOpt.flatMap { gg =>
      val s0 = gg.text
      if (s0.isEmpty) None else Some(s0)
    }

    def name: String = nameOption.getOrElse("")

    def name_=(value: String): Unit =
      ggNameOpt.foreach(_.text = value)

    def value: EnvSegment = {
      val curveI = mCurve.selectedItem.fold(-1)(nCurve.indexOf)
      val curve0 = if (curveI < 0) Curve.linear else sqCurve(curveI)
      val curve  = curve0 match {
        case p @ Curve.parametric(_) => p.copy(curvature = mParam.getNumber.floatValue())
        case other => other
      }
      EnvSegment.Single(
        startLevel = mStartLvl.getNumber.doubleValue(), curve = curve
      )
    }

    def value_=(value: EnvSegment): Unit = {
      val startLvl = value.startLevels.headOption.getOrElse(0.0)  // XXX TODO support multi
      mStartLvl.setValue(startLvl)
      val warpNorm = value.curve match {
        case p @ Curve.parametric(c) =>
          mParam.setValue(c)
          p.copy(curvature = 0.0f)
        case other => other
      }
      val warpI = sqCurve.indexOf(warpNorm)
      mCurve.selectedItem = if (warpI < 0) None else Some(nCurve(warpI))

      updateParam()
    }

    private def mkSpinner(m: SpinnerModel): Spinner = {
      val res = new Spinner(m)
      res.listenTo(res)
      res.reactions += {
        case ValueChanged(_) => dispatch(())
      }
      res
    }

    val ggStartLvl: Component = mkSpinner(mStartLvl)

    private[this] val ggParam   = mkSpinner(mParam)
    private[this] val ggCurve   = new ComboBox(mCurve) {
      listenTo(selection)
      reactions += {
        case SelectionChanged(_) => updateParam()
      }
    }

    private[this] val lbName      = {
      val res = new Label("Name:", EmptyIcon, Alignment.Right)
      if (nameIn.isEmpty) res.visible = false
      res
    }
    private[this] val lbStartLvl  = new Label( "Start Level:", EmptyIcon, Alignment.Right)
    private[this] val lbCurve     = new Label(       "Curve:", EmptyIcon, Alignment.Right)
    private[this] val lbParam     = new Label(   "Curvature:", EmptyIcon, Alignment.Right)

    private def updateParam(fire: Boolean = true): Unit = {
      val v = value // updateExample(fire = false)
      ggParam.enabled = editable && v.curve.isInstanceOf[Curve.parametric]
      if (fire) dispatch(())
    }

    updateParam(fire = false)

    private[this] val boxParams = new GroupPanel {
      horizontal= Seq(
        Par(Trailing)(lbName, lbStartLvl, lbCurve, lbParam),
        Par          (ggName, ggStartLvl, ggCurve, ggParam)
      )
      vertical = Seq(
        Par(Baseline)(lbName    , ggName    ),
        Par(Baseline)(lbStartLvl, ggStartLvl),
        Par(Baseline)(lbCurve   , ggCurve   ),
        Par(Baseline)(lbParam   , ggParam   )
      )
    }

//    private[this] val box = new BoxPanel(Orientation.Vertical)
//    box.contents += boxParams
//    box.contents += Swing.VStrut(8)
//    box.contents += boxDemo

    if (!editable) {
      ggStartLvl.enabled = false
      ggCurve   .enabled = false
      ggParam   .enabled = false
    }

    def component: Component = boxParams // box
  }

  // XXX TODO DRY with ParamSpecObjView
  private final class ViewImpl[S <: Sys[S]](objH: stm.Source[S#Tx, EnvSegment.Obj[S]], val editable: Boolean)
                                           (implicit val universe: Universe[S],
                                            val undoManager: UndoManager)
    extends UniverseView[S] with View.Editable[S] with ComponentHolder[Component] {

    type C = Component

    private[this] var value       : EnvSegment        = _
    private[this] var panel       : PanelImpl         = _
    // private[this] val _dirty      : Ref[Boolean]      = Ref(false)
    // private[this] var actionApply : Action            = _
    private[this] var observer    : Disposable[S#Tx]  = _

    def init(obj0: EnvSegment.Obj[S])(implicit tx: S#Tx): this.type = {
      val value0 = obj0.value
      deferTx(guiInit(value0))
      observer = obj0.changed.react { implicit tx => upd =>
        deferTx {
          value       = upd.now
          panel.value = upd.now
        }
      }
      this
    }

    private def guiInit(value0: EnvSegment): Unit = {
      this.value  = value0
      panel       = new PanelImpl(nameIn = None, editable = editable)
      panel.value = value0

      panel.addListener {
        case _ => save() // updateDirty()
      }

//      actionApply = Action("Apply") {
//        save()
//        updateDirty()
//      }

//      actionApply.enabled = false
//      val ggApply   = GUI.toolButton(actionApply, raphael.Shapes.Check, tooltip = "Save changes")
//      val panelBot  = new FlowPanel(FlowPanel.Alignment.Trailing)(ggApply)

//      component = new BorderPanel {
//        add(panel.component, BorderPanel.Position.Center)
//        add(panelBot       , BorderPanel.Position.South )
//      }
      component = panel.component
    }

//    def dirty(implicit tx: S#Tx): Boolean = _dirty.get(tx.peer)

//    private def updateDirty(): Unit = {
//      val valueNow  = panel.value
//      val isDirty   = value !== valueNow
//      val wasDirty  = _dirty.single.swap(isDirty)
//      if (isDirty !== wasDirty) actionApply.enabled = isDirty
//    }

    def save(): Unit = {
      requireEDT()
      val newValue = panel.value
      val editOpt = cursor.step { implicit tx =>
        val title = s"Edit $humanName"
        objH() match {
          case EnvSegment.Obj.Var(pVr) =>
            val oldVal  = pVr.value
            if (newValue === oldVal) None else {
              val pVal    = EnvSegment.Obj.newConst[S](newValue)
              val edit    = EditVar.Expr[S, EnvSegment, EnvSegment.Obj](title, pVr, pVal)
              Some(edit)
            }

          case EnvSegment.Obj.ApplySingle(DoubleObj.Var(startVr), CurveObj.Var(curveVr)) =>
            var edits = List.empty[UndoableEdit]
            val newCurve = newValue.curve
            if (curveVr.value !== newCurve) {
              val curveVal = CurveObj.newConst[S](newCurve)
              edits ::= EditVar.Expr[S, Curve, CurveObj](title, curveVr, curveVal)
            }
            val newStart = newValue.startLevels.headOption.getOrElse(0.0) // XXX TODO
            if (startVr.value !== newStart) {
              val startVal = DoubleObj.newConst[S](newStart)
              edits ::= EditVar.Expr[S, Double, DoubleObj](title, startVr, startVal)
            }
            CompoundEdit(edits, title)

          case _ => None
        }
      }
      editOpt.foreach { edit =>
        undoManager.add(edit)
        // undoManager.clear()
        value = newValue
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = observer.dispose()
  }

  // XXX TODO DRY with ParamSpecObjView
  private final class FrameImpl[S <: Sys[S]](val view: ViewImpl[S],
                                             name: CellView[S#Tx, String])
    extends WindowImpl[S](name) /* with Veto[S#Tx] */ {

    //    resizable = false

//    override def prepareDisposal()(implicit tx: S#Tx): Option[Veto[S#Tx]] =
//      if (!view.editable || !view.dirty) None else Some(this)
//
//    private def _vetoMessage = "The object has been edited."
//
//    def vetoMessage(implicit tx: S#Tx): String = _vetoMessage
//
//    def tryResolveVeto()(implicit tx: S#Tx): Future[Unit] = {
//      val p = Promise[Unit]()
//      deferTx {
//        val message = s"${_vetoMessage}\nDo you want to save the changes?"
//        val opt = OptionPane.confirmation(message = message, optionType = OptionPane.Options.YesNoCancel,
//          messageType = OptionPane.Message.Warning)
//        opt.title = s"Close - $title"
//        opt.show(Some(window)) match {
//          case OptionPane.Result.No =>
//            p.success(())
//
//          case OptionPane.Result.Yes =>
//            /* val fut = */ view.save()
//            p.success(())
//
//          case OptionPane.Result.Cancel | OptionPane.Result.Closed =>
//            p.failure(Aborted())
//        }
//      }
//      p.future
//    }
  }

  private def detectEditable[S <: Sys[S]](obj: E[S]): Boolean =
    obj match {
      case EnvSegment.Obj.Var(_) => true
      case EnvSegment.Obj.ApplySingle(DoubleObj.Var(_), CurveObj.Var(_)) => true
      case _ => false // XXX TODO --- support multi
    }

  def mkGraphemeView[S <: Sys[S]](entry: Entry[S], obj: E[S], mode: GraphemeView.Mode)
                                 (implicit tx: S#Tx): GraphemeObjView[S] = {
    val value     = obj.value
    val editable  = detectEditable(obj)
    new GraphemeImpl[S](tx.newHandle(entry), tx.newHandle(obj), value = value, isEditable = editable)
      .init(obj, entry)
  }

  private implicit object ReadCurve extends scopt.Read[Curve] {
    def arity: Int = 1

    private[this] val map: Map[String, Curve] = Map(
      "step"        -> Curve.step,
      "lin"         -> Curve.linear,
      "linear"      -> Curve.linear,
      "exp"         -> Curve.exponential,
      "exponential" -> Curve.exponential,
      "sin"         -> Curve.sine,
      "sine"        -> Curve.sine,
      "welch"       -> Curve.welch,
      "sqr"         -> Curve.squared,
      "squared"     -> Curve.squared,
      "cub"         -> Curve.cubed,
      "cubed"       -> Curve.cubed
    )

    def reads: String => Curve = { s =>
      map.getOrElse(s.toLowerCase, {
        val p = s.toFloat
        Curve.parametric(p)
      })
    }
  }

  // XXX TODO DRY with ParamSpecObjView
  override def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])(done: MakeResult[S] => Unit)
                                          (implicit universe: Universe[S]): Unit = {
    val panel = new PanelImpl(nameIn = Some(prefix), editable = true)

    val pane = desktop.OptionPane.confirmation(panel.component, optionType = Dialog.Options.OkCancel,
      messageType = Dialog.Message.Question, focus = Some(panel.ggStartLvl))
    pane.title  = s"New $humanName"
    val res = pane.show(window)

    val res1 = if (res === Dialog.Result.Ok) {
      Success(Config[S](name = panel.name, value = panel.value))
    } else {
      Failure(Aborted())
    }
    done(res1)
  }

  override def initMakeCmdLine[S <: Sys[S]](args: List[String])(implicit universe: Universe[S]): MakeResult[S] = {
    val default: Config[S] = Config()
    val p = ObjViewCmdLineParser[S](this)
    import p._
    name((v, c) => c.copy(name = v))
    opt[Unit]('c', "const")
      .text(s"Make constant instead of variable")
      .action((_, c) => c.copy(const = true))

    arg[Seq[Double]]("start-level")
      .text("Starting level (single double or comma separated doubles)")
      .required()
      .action { (v, c) =>
        val newValue = v match {
          case Seq(single)  => EnvSegment.Single(single         , c.value.curve)
          case _            => EnvSegment.Multi (v.toIndexedSeq , c.value.curve)
        }
        c.copy(value = newValue)
      }

    arg[Curve]("curve")
      .text(s"Parameter warp or curve (default: ${"lin" /* default.value.curve */})")
      .action { (v, c) =>
        val newValue = c.value match {
          case e: EnvSegment.Single => e.copy(curve = v)
          case e: EnvSegment.Multi  => e.copy(curve = v)
        }
        c.copy(value = newValue)
      }


    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config._
    val obj0  = EnvSegment.Obj.newConst[S](value)
    val obj   = if (const) obj0 else EnvSegment.Obj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // ---- basic ----

  private abstract class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, E[S]])
    extends ObjViewImpl.Impl[S]
      with ObjViewImpl.ExprLike[S, V, E] with EnvSegmentObjView[S] {

    final def isViewable: Boolean = true

    final def factory: ObjView.Factory = EnvSegmentObjView

    final def exprType: Type.Expr[V, E] = EnvSegment.Obj

    final def expr(implicit tx: S#Tx): E[S] = objH()

    protected def isEditable: Boolean

    override def obj(implicit tx: S#Tx): E[S] = objH()

    override def openView(parent: Option[Window[S]])(implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      implicit val undo: UndoManager = UndoManager()
      val _obj      = obj
      val view      = new ViewImpl[S](objH, editable = isEditable).init(_obj)
      val nameView  = CellView.name(_obj)
      val fr        = new FrameImpl[S](view, nameView).init()
      Some(fr)
    }
  }

  // ---- ListObjView ----

  private final class ListImpl[S <: Sys[S]](objH: stm.Source[S#Tx, E[S]], var value: V, val isEditable: Boolean)
    extends Impl(objH) with ListObjView[S]
      with ListObjViewImpl.SimpleExpr[S, V, E]
//      with ListObjViewImpl.NonEditable[S]
      with ListObjViewImpl.StringRenderer {

    def convertEditValue(v: Any): Option[V] = None

//    def isEditable: Boolean = false
  }

  // ---- GraphemeObjView ----

  private final class GraphemeImpl[S <: Sys[S]](val entryH: stm.Source[S#Tx, Entry[S]],
                                                objH: stm.Source[S#Tx, E[S]],
                                                var value: V, val isEditable: Boolean)
    extends Impl[S](objH)
      with GraphemeObjViewImpl.SimpleExpr[S, V, E]
      with GraphemeObjView.HasStartLevels[S] {

    private[this] val allSame =
      value.numChannels <= 1 || { val v0 = value.startLevels; val vh = v0.head; v0.forall(_ === vh) }

    def startLevels: Vec[Double] = value.startLevels

    def insets: Insets = GraphemeObjView.DefaultInsets

    private[this] var succOpt = Option.empty[HasStartLevels[S]]

    override def succ_=(opt: Option[GraphemeObjView[S]])(implicit tx: S#Tx): Unit = deferTx {
      succOpt = opt.collect {
        case hs: HasStartLevels[S] => hs
      }
      // XXX TODO --- fire repaint?
    }

    override def paintBack(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = succOpt match {
      case Some(succ) =>
        val startLvl  = value.startLevels
        val endLvl    = succ .startLevels
        val numChS    = startLvl.size
        val numChE    = endLvl  .size
        if (numChS === 0 || numChE === 0) return

        val numCh         = math.max(numChS, numChE)
        val c             = gv.canvas
        val startSelected = gv.selectionModel.contains(this)
        val endSelected   = gv.selectionModel.contains(succ)
        val startTime0    = this.timeValue
        val endTime0      = succ.timeValue
        val startTime     = if (startSelected ) startTime0  + r.ttMoveState.deltaTime else startTime0
        val endTime       = if (endSelected   ) endTime0    + r.ttMoveState.deltaTime else endTime0
        val x1            = c.frameToScreen(startTime)
        val x2            = c.frameToScreen(endTime)
        g.setStroke(r.strokeInletSpan)
        g.setPaint (r.pntInletSpan)
        val path      = r.shape1
        path.reset()

        var ch = 0
        while (ch < numCh) {
          val startValue0 = startLvl(ch % numChS)
          val startValue  = if (startSelected ) startValue0 + r.ttMoveState.deltaModelY else startValue0
          val y1          = c.modelPosToScreen(startValue)
          val endValue0   = endLvl  (ch % numChE)
          val endValue    = if (endSelected   ) endValue0   + r.ttMoveState.deltaModelY else endValue0
          val y2          = c.modelPosToScreen(endValue)
          path.moveTo(x1, y1)

          value.curve match {
            case Curve.linear =>
            case Curve.step   =>
              path.lineTo(x2, y1)

            case curve =>
              var x   = x1 + 4
              val y1f = y1.toFloat
              val y2f = y2.toFloat
              val dx  = x2 - x1
              if (dx > 0) while (x < x2) {
                val pos = ((x - x1) / dx).toFloat
                val y = curve.levelAt(pos, y1f, y2f)
                path.lineTo(x, y)
                x += 4
              }
              // XXX TODO (what?)

          }

          path.lineTo(x2, y2)
          ch += 1
        }
        g.draw(path)

      case _ =>
    }

    override def paintFront(g: Graphics2D, gv: GraphemeView[S], r: GraphemeRendering): Unit = {
      if (value.numChannels === 0) return
      val levels = value.startLevels
      if (allSame) {
        DoubleObjView.graphemePaintFront(this, levels.head, g, gv, r)
        return
      }

      val c     = gv.canvas
      val jc    = c.canvasComponent.peer
      val h     = jc.getHeight
      val x     = c.frameToScreen(timeValue)

      val a1    = r.area1
      val a2    = r.area2
      val p     = r.ellipse1 // r.shape1
      a1.reset()
      a2.reset()
      val hm    = h - 1
      var ch    = 0
      val numCh = levels.size
      var min   = Double.MaxValue
      var max   = Double.MinValue
      while (ch < numCh) {
        val v = levels(ch)
        val y = (1 - v) * hm
        p.setFrame(x - 2, y - 2, 4, 4)
        a1.add(new Area(p))
        p.setFrame(x - HandleRadius, y - HandleRadius, HandleDiameter, HandleDiameter)
        a2.add(new Area(p))
        if (y < min) min = y
        if (y > max) max = y
        ch += 1
      }

      g.setStroke(r.strokeInletSpan)
      g.setPaint (r.pntInletSpan)
      val ln = r.shape1
      ln.reset()
      ln.moveTo(x, min)
      ln.lineTo(x, max)
      g.draw(ln)
      g.setStroke(r.strokeNormal)
      val selected = gv.selectionModel.contains(this)
      g.setPaint(if (selected) r.pntRegionBackgroundSelected else r.pntRegionBackground)
      g.fill(a1)
      g.setPaint(if (selected) r.pntRegionOutlineSelected else r.pntRegionOutline)
      g.draw(a2)
    }
  }
}
trait EnvSegmentObjView[S <: stm.Sys[S]] extends ObjView[S] {
  override def obj(implicit tx: S#Tx): EnvSegment.Obj[S]
}