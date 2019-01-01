/*
 *  ObjViewImpl.scala
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

import java.awt.geom.Path2D
import java.awt.{Color => AWTColor}

import de.sciss.audiowidgets.AxisFormat
import de.sciss.desktop
import de.sciss.desktop.OptionPane
import de.sciss.icons.raphael
import de.sciss.kollflitz.Vec
import de.sciss.lucre.confluent.Access
import de.sciss.lucre.event.impl.ObservableImpl
import de.sciss.lucre.expr.{BooleanObj, CellView, Expr, LongObj, StringObj, Type}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.{View, Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditFolderInsertObj
import de.sciss.mellite.gui.impl.component.PaintIcon
import de.sciss.mellite.gui.impl.document.NuagesEditorFrameImpl
import de.sciss.mellite.gui.impl.{ExprHistoryView, WindowImpl}
import de.sciss.mellite.gui.{EnsembleFrame, FolderFrame, GUI, GraphemeFrame, ListObjView, ObjView, Shapes, TimelineFrame}
import de.sciss.mellite.{Cf, Mellite}
import de.sciss.serial.Serializer
import de.sciss.swingplus.{GroupPanel, Spinner}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.gui.UniverseView
import de.sciss.synth.proc.{Confluent, ObjKeys, TimeRef, Universe, Workspace}
import javax.swing.undo.UndoableEdit
import javax.swing.{Icon, SpinnerNumberModel, UIManager}

import scala.language.higherKinds
import scala.swing.Swing.EmptyIcon
import scala.swing.{Action, Alignment, BorderPanel, Button, CheckBox, ColorChooser, Component, Dialog, FlowPanel, GridPanel, Label, Swing, TextField}
import scala.util.Try

object ObjViewImpl {
  import java.lang.{String => _String}

  import de.sciss.lucre.expr.{IntVector => _IntVector}
  import de.sciss.lucre.stm.{Folder => _Folder}
  import de.sciss.nuages.{Nuages => _Nuages}
  import de.sciss.synth.proc.{Color => _Color, Ensemble => _Ensemble, FadeSpec => _FadeSpec, Grapheme => _Grapheme, Timeline => _Timeline}

  import scala.{Boolean => _Boolean, Long => _Long}

  def nameOption[S <: stm.Sys[S]](obj: Obj[S])(implicit tx: S#Tx): Option[_String] =
    obj.attr.$[StringObj](ObjKeys.attrName).map(_.value)

  // -------- String --------

  object String extends ListObjView.Factory {
    type E[~ <: stm.Sys[~]] = StringObj[~]
    val icon          : Icon      = raphaelIcon(raphael.Shapes.Font)
    val prefix        : _String   = "String"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = StringObj
    def category      : _String   = ObjView.categPrimitives
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: StringObj[S])(implicit tx: S#Tx): ListObjView[S] = {
      val ex          = obj
      val value       = ex.value
      val isEditable  = ex match {
        case StringObj.Var(_)  => true
        case _            => false
      }
      val isViewable  = tx.isInstanceOf[Confluent.Txn]
      new String.Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
    }

    type Config[S <: stm.Sys[S]] = PrimitiveConfig[_String]

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val ggValue   = new TextField(20)
      ggValue.text  = "Value"
      val res = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = Some(ggValue.text))
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](config: (_String, _String))(implicit tx: S#Tx): List[Obj[S]] = {
      val (name, value) = config
      val obj = StringObj.newVar(StringObj.newConst[S](value))
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, StringObj[S]],
                                 var value: _String,
                                 override val isEditable: _Boolean, val isViewable: _Boolean)
      extends ListObjView[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, _String, StringObj]
      with ListObjViewImpl.StringRenderer {

      type E[~ <: stm.Sys[~]] = StringObj[~]

      def factory: ObjView.Factory = String

      def exprType: Type.Expr[String, StringObj] = StringObj

      def convertEditValue(v: Any): Option[_String] = Some(v.toString)

      def expr(implicit tx: S#Tx): StringObj[S] = objH()
    }
  }

  // -------- Long --------

  object Long extends ListObjView.Factory {
    type E[S <: stm.Sys[S]] = LongObj[S]
    val icon          : Icon      = raphaelIcon(Shapes.IntegerNumber)  // XXX TODO
    val prefix        : _String   = "Long"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = LongObj
    def category      : _String   = ObjView.categPrimitives
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: LongObj[S])(implicit tx: S#Tx): ListObjView[S] = {
      val ex          = obj
      val value       = ex.value
      val isEditable  = ex match {
        case LongObj.Var(_)  => true
        case _            => false
      }
      val isViewable  = tx.isInstanceOf[Confluent.Txn]
      new Long.Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
    }

    type Config[S <: stm.Sys[S]] = PrimitiveConfig[_Long]

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val model     = new SpinnerNumberModel(0L, _Long.MinValue, _Long.MaxValue, 1L)
      val ggValue   = new Spinner(model)
      val res = primitiveConfig[S, _Long](window, tpe = prefix, ggValue = ggValue, prepare =
        Some(model.getNumber.longValue()))
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](config: (String, _Long))(implicit tx: S#Tx): List[Obj[S]] = {
      val (name, value) = config
      val obj = LongObj.newVar(LongObj.newConst[S](value))
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, LongObj[S]],
                                  var value: _Long,
                                  override val isEditable: _Boolean, val isViewable: _Boolean)
      extends ListObjView /* .Long */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, _Long, LongObj]
      with ListObjViewImpl.StringRenderer {

      type E[~ <: stm.Sys[~]] = LongObj[~]

      def factory: ObjView.Factory = Long

      def exprType: Type.Expr[_Long, LongObj] = LongObj

      def expr(implicit tx: S#Tx): LongObj[S] = objH()

      def convertEditValue(v: Any): Option[_Long] = v match {
        case num: _Long => Some(num)
        case s: _String => Try(s.toLong).toOption
      }
    }
  }

  // -------- Boolean --------

  object Boolean extends ListObjView.Factory {
    type E[S <: stm.Sys[S]] = BooleanObj[S]
    val icon          : Icon      = raphaelIcon(Shapes.BooleanNumber)
    val prefix        : _String   = "Boolean"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = BooleanObj
    def category      : _String   = ObjView.categPrimitives
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: BooleanObj[S])(implicit tx: S#Tx): ListObjView[S] = {
      val ex          = obj
      val value       = ex.value
      val isEditable  = ex match {
        case BooleanObj.Var(_)  => true
        case _            => false
      }
      val isViewable  = tx.isInstanceOf[Confluent.Txn]
      new Boolean.Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
    }

    type Config[S <: stm.Sys[S]] = PrimitiveConfig[_Boolean]

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val ggValue = new CheckBox()
      val res = primitiveConfig[S, _Boolean](window, tpe = prefix, ggValue = ggValue, prepare = Some(ggValue.selected))
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](config: (String, _Boolean))(implicit tx: S#Tx): List[Obj[S]] = {
      val (name, value) = config
      val obj = BooleanObj.newVar(BooleanObj.newConst[S](value))
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, BooleanObj[S]],
                                  var value: _Boolean,
                                  override val isEditable: _Boolean, val isViewable: Boolean)
      extends ListObjView /* .Boolean */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.BooleanExprLike[S]
      with ListObjViewImpl.SimpleExpr[S, _Boolean, BooleanObj] {

      type E[~ <: stm.Sys[~]] = BooleanObj[~]

      def factory: ObjView.Factory = Boolean

      def expr(implicit tx: S#Tx): BooleanObj[S] = objH()
    }
  }

  // -------- IntVector --------

  object IntVector extends ListObjView.Factory {
    type E[S <: stm.Sys[S]] = _IntVector[S]
    val icon          : Icon      = raphaelIcon(Shapes.IntegerNumberVector)
    val prefix        : _String   = "IntVector"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = _IntVector
    def category      : _String   = ObjView.categPrimitives
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _IntVector[S])(implicit tx: S#Tx): ListObjView[S] = {
      val ex          = obj
      val value       = ex.value
      val isEditable  = ex match {
        case _IntVector.Var(_)  => true
        case _            => false
      }
      val isViewable  = tx.isInstanceOf[Confluent.Txn]
      new IntVector.Impl[S](tx.newHandle(obj), value, isEditable = isEditable, isViewable = isViewable).init(obj)
    }

    type Config[S <: stm.Sys[S]] = PrimitiveConfig[Vec[Int]]

    private def parseString(s: String): Option[Vec[Int]] =
      Try(s.split(" ").iterator.map(x => x.trim().toInt).toIndexedSeq).toOption

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val ggValue = new TextField("0 0")
      val res = primitiveConfig(window, tpe = prefix, ggValue = ggValue, prepare = parseString(ggValue.text))
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](config: (String, Vec[Int]))(implicit tx: S#Tx): List[Obj[S]] = {
      val (name, value) = config
      val obj = _IntVector.newVar(_IntVector.newConst[S](value))
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _IntVector[S]], var value: Vec[Int],
                                  override val isEditable: _Boolean, val isViewable: _Boolean)
      extends ListObjView[S]
        with ObjViewImpl.Impl[S]
        with ListObjViewImpl.SimpleExpr[S, Vec[Int], _IntVector] {

      type E[~ <: stm.Sys[~]] = _IntVector[~]

      def factory: ObjView.Factory = IntVector

      def exprType: Type.Expr[Vec[Int], _IntVector] = _IntVector

      def expr(implicit tx: S#Tx): _IntVector[S] = objH()

      def convertEditValue(v: Any): Option[Vec[Int]] = v match {
        case num: Vec[_] => num.foldLeft(Option(Vec.empty[Int])) {
          case (Some(prev), d: Int) => Some(prev :+ d)
          case _ => None
        }
        case s: _String  => IntVector.parseString(s)
      }

      def configureRenderer(label: Label): Component = {
        label.text = value.mkString(" ")
        label
      }
    }
  }

  // -------- Color --------

  object Color extends ListObjView.Factory {
    type E[~ <: stm.Sys[~]] = _Color.Obj[~]
    val icon          : Icon      = raphaelIcon(raphael.Shapes.Paint)
    val prefix        : _String   = "Color"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = _Color.Obj
    def category      : _String   = ObjView.categOrganisation
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _Color.Obj[S])(implicit tx: S#Tx): ListObjView[S] = {
      val ex          = obj
      val value       = ex.value
      val isEditable  = ex match {
        case _Color.Obj.Var(_)  => true
        case _                  => false
      }
      new Color.Impl[S](tx.newHandle(obj), value, isEditable0 = isEditable).init(obj)
    }

    type Config[S <: stm.Sys[S]] = PrimitiveConfig[_Color]

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val (ggValue, ggChooser) = mkColorEditor()
      val res = primitiveConfig[S, _Color](window, tpe = prefix, ggValue = ggValue, prepare =
        Some(fromAWT(ggChooser.color)))
      res.foreach(ok(_))
    }

    private def mkColorEditor(): (Component, ColorChooser) = {
      val chooser = new ColorChooser()
      val bPredef = _Color.Palette.map { colr =>
        val action: Action = new Action(null /* colr.name */) {
          private val awtColor = toAWT(colr)
          icon = new PaintIcon(awtColor, 32, 32)
          def apply(): Unit = chooser.color = awtColor
        }
        val b = new Button(action)
        // b.horizontalAlignment = Alignment.Left
        b.focusable = false
        b
      }
      val pPredef = new GridPanel(4, 4)
      pPredef.contents ++= bPredef
      val panel = new BorderPanel {
        add(pPredef, BorderPanel.Position.West  )
        add(chooser, BorderPanel.Position.Center)
      }
      (panel, chooser)
    }

    def toAWT(c: _Color): java.awt.Color = new java.awt.Color(c.rgba)
    def fromAWT(c: java.awt.Color): _Color = {
      val rgba = c.getRGB
      _Color.Palette.find(_.rgba == rgba).getOrElse(_Color.User(rgba))
    }

    def makeObj[S <: Sys[S]](config: (String, _Color))(implicit tx: S#Tx): List[Obj[S]] = {
      val (name, value) = config
      val obj = _Color.Obj.newVar(_Color.Obj.newConst[S](value))
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _Color.Obj[S]],
                                  var value: _Color, isEditable0: _Boolean)
      extends ListObjView /* .Color */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.SimpleExpr[S, _Color, _Color.Obj] {

      type E[~ <: stm.Sys[~]] = _Color.Obj[~]

      def isEditable = false    // not until we have proper editing components

      def factory: ObjView.Factory = Color

      def exprType: Type.Expr[_Color, _Color.Obj] = _Color.Obj

      def expr(implicit tx: S#Tx): _Color.Obj[S] = objH()

      def configureRenderer(label: Label): Component = {
        // renderers are used for "stamping", so we can reuse a single object.
        label.icon = ListIcon
        ListIcon.paint = Color.toAWT(value)
        label
      }

      def convertEditValue(v: Any): Option[_Color] = v match {
        case c: _Color  => Some(c)
        case _          => None
      }

      def isViewable: _Boolean = isEditable0

      override def openView(parent: Option[Window[S]])
                           (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
//        val opt = OptionPane.confirmation(message = component, optionType = OptionPane.Options.OkCancel,
//          messageType = OptionPane.Message.Plain)
//        opt.show(parent) === OptionPane.Result.Ok
        val title = CellView.name(obj)
        val w: WindowImpl[S] = new WindowImpl[S](title) { self =>
          val view: View[S] = View.wrap {
            val (compColor, chooser) = Color.mkColorEditor()
            chooser.color = Color.toAWT(value)
            val ggCancel = Button("Cancel") {
              closeMe() // self.handleClose()
            }

            def apply(): Unit = {
              val colr = Color.fromAWT(chooser.color)
              import universe.cursor
              val editOpt = cursor.step { implicit tx =>
                objH() match {
                  case _Color.Obj.Var(vr) =>
                    Some(EditVar.Expr[S, _Color, _Color.Obj]("Change Color", vr, _Color.Obj.newConst[S](colr)))
                  case _ => None
                }
              }
              editOpt.foreach { edit =>
                parent.foreach { p =>
                  p.view match {
                    case e: View.Editable[S] => e.undoManager.add(edit)
                  }
                }
              }
            }

            val ggOk = Button("Ok") {
              apply()
              closeMe() // self.handleClose()
            }
            val ggApply = Button("Apply") {
              apply()
            }
            val pane = new BorderPanel {
              add(compColor, BorderPanel.Position.Center)
              add(new FlowPanel(ggOk, ggApply, Swing.HStrut(8), ggCancel), BorderPanel.Position.South)
            }
            pane
          }

          def closeMe(): Unit = {
            import universe.cursor
            cursor.step { implicit tx => self.dispose() }
          }

          init()
        }
        Some(w)
      }
    }

    private val ListIcon = new PaintIcon(java.awt.Color.black, 48, 16)
  }

  // -------- Folder --------

  object Folder extends ListObjView.Factory {
    type E[~ <: stm.Sys[~]] = _Folder[~]
    def icon          : Icon      = UIManager.getIcon("Tree.openIcon")  // Swing.EmptyIcon
    val prefix        : _String   = "Folder"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = _Folder
    def category      : _String   = ObjView.categOrganisation
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _Folder[S])(implicit tx: S#Tx): ListObjView[S] =
      new Folder.Impl[S](tx.newHandle(obj)).initAttrs(obj)

    type Config[S <: stm.Sys[S]] = _String

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
        messageType = OptionPane.Message.Question, initial = prefix)
      opt.title = "New Folder"
      val res = opt.show(window)
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](name: _String)(implicit tx: S#Tx): List[Obj[S]] = {
      val obj  = _Folder[S]
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    // XXX TODO: could be viewed as a new folder view with this folder as root
    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _Folder[S]])
      extends ListObjView /* .Folder */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.EmptyRenderer[S]
      with ListObjViewImpl.NonEditable[S] {

      type E[~ <: stm.Sys[~]] = _Folder[~]

      def factory: ObjView.Factory = Folder

      def isViewable = true

      def openView(parent: Option[Window[S]])
                  (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
        val folderObj = objH()
        val nameView  = CellView.name(folderObj)
        Some(FolderFrame(nameView, folderObj))
      }
    }
  }

  // -------- Timeline --------

  object Timeline extends ListObjView.Factory {
    type E[S <: stm.Sys[S]] = _Timeline[S]
    val icon          : Icon      = raphaelIcon(raphael.Shapes.Ruler)
    val prefix        : _String   = "Timeline"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = _Timeline
    def category      : _String   = ObjView.categComposition
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _Timeline[S])(implicit tx: S#Tx): ListObjView[S] =
      new Timeline.Impl(tx.newHandle(obj)).initAttrs(obj)

    type Config[S <: stm.Sys[S]] = _String

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
        messageType = OptionPane.Message.Question, initial = prefix)
      opt.title = s"New $prefix"
      val res = opt.show(window)
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](name: _String)(implicit tx: S#Tx): List[Obj[S]] = {
      val obj = _Timeline[S] // .Modifiable[S]
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _Timeline[S]])
      extends ListObjView /* .Timeline */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.EmptyRenderer[S]
      with ListObjViewImpl.NonEditable[S] {

      type E[~ <: stm.Sys[~]] = _Timeline[~]

      def factory: ObjView.Factory = Timeline

      def isViewable = true

      def openView(parent: Option[Window[S]])
                  (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
        val frame = TimelineFrame[S](objH())
        Some(frame)
      }
    }
  }

  // -------- Grapheme --------

  object Grapheme extends ListObjView.Factory {
    type E[S <: stm.Sys[S]] = _Grapheme[S]
    val icon          : Icon      = raphaelIcon(raphael.Shapes.LineChart)
    val prefix        : _String   = "Grapheme"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = _Grapheme
    def category      : _String   = ObjView.categComposition
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _Grapheme[S])(implicit tx: S#Tx): ListObjView[S] =
      new Grapheme.Impl(tx.newHandle(obj)).initAttrs(obj)

    type Config[S <: stm.Sys[S]] = _String

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
        messageType = OptionPane.Message.Question, initial = prefix)
      opt.title = s"New $prefix"
      val res = opt.show(window)
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](name: _String)(implicit tx: S#Tx): List[Obj[S]] = {
      val obj = _Grapheme[S] // .Modifiable[S]
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _Grapheme[S]])
      extends ListObjView /* .Grapheme */[S]
        with ObjViewImpl.Impl[S]
        with ListObjViewImpl.EmptyRenderer[S]
        with ListObjViewImpl.NonEditable[S] {

      type E[~ <: stm.Sys[~]] = _Grapheme[~]

      def factory: ObjView.Factory = Grapheme

      def isViewable = true

      def openView(parent: Option[Window[S]])
                  (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
        val frame = GraphemeFrame[S](objH())
        Some(frame)
      }
    }
  }

  // -------- FadeSpec --------

  object FadeSpec extends ListObjView.Factory {
    type E[~ <: stm.Sys[~]] = _FadeSpec.Obj[~]
    val icon          : Icon      = raphaelIcon(Shapes.Aperture)
    val prefix        : _String   = "FadeSpec"
    val humanName     : _String   = "Fade"
    def tpe           : Obj.Type  = _FadeSpec.Obj
    def category      : _String   = ObjView.categComposition
    def hasMakeDialog : Boolean   = false

    def mkListView[S <: Sys[S]](obj: _FadeSpec.Obj[S])(implicit tx: S#Tx): ListObjView[S] = {
      val value   = obj.value
      new FadeSpec.Impl[S](tx.newHandle(obj), value).init(obj)
    }

    type Config[S <: stm.Sys[S]] = Unit

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      None // XXX TODO
//      val ggShape = new ComboBox()
//      Curve.cubed
//      val ggValue = new ComboBox(Seq(_Code.FileTransform.name, _Code.SynthGraph.name))
//      actionAddPrimitive(folderH, window, tpe = prefix, ggValue = ggValue, prepare = ...
//      ) { implicit tx =>
//        value =>
//          val peer = _FadeSpec.Expr(numFrames, shape, floor)
//          _FadeSpec.Obj(peer)
//      }
    }

    def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = Nil

    private val timeFmt = AxisFormat.Time(hours = false, millis = true)

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _FadeSpec.Obj[S]], var value: _FadeSpec)
      extends ListObjView /* .FadeSpec */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.NonEditable[S]
      with NonViewable[S] {

      type E[~ <: stm.Sys[~]] = _FadeSpec.Obj[~]

      def factory: ObjView.Factory = FadeSpec

      def init(obj: _FadeSpec.Obj[S])(implicit tx: S#Tx): this.type = {
        initAttrs(obj)
        disposables ::= obj.changed.react { implicit tx => upd =>
          deferAndRepaint {
            value = upd.now
          }
        }
        this
      }

      def configureRenderer(label: Label): Component = {
        val sr = TimeRef.SampleRate // 44100.0
        val dur = timeFmt.format(value.numFrames.toDouble / sr)
        label.text = s"$dur, ${value.curve}"
        label
      }
    }
  }

  // -------- Ensemble --------

  object Ensemble extends ListObjView.Factory {
    type E[~ <: stm.Sys[~]] = _Ensemble[~]
    val icon          : Icon      = raphaelIcon(raphael.Shapes.Cube2)
    val prefix        : _String   = "Ensemble"
    def humanName     : _String   = prefix
    def tpe           : Obj.Type  = _Ensemble
    def category      : _String   = ObjView.categComposition
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _Ensemble[S])(implicit tx: S#Tx): ListObjView[S] = {
      val ens         = obj
      val playingEx   = ens.playing
      val playing     = playingEx.value
      val isEditable  = playingEx match {
        case BooleanObj.Var(_)  => true
        case _            => false
      }
      new Ensemble.Impl[S](tx.newHandle(obj), playing = playing, isEditable = isEditable).init(obj)
    }

    final case class Config[S <: stm.Sys[S]](name: String, offset: Long, playing: Boolean)

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val ggName    = new TextField(10)
      ggName.text   = prefix
      val offModel  = new SpinnerNumberModel(0.0, 0.0, 1.0e6 /* _Double.MaxValue */, 0.1)
      val ggOff     = new Spinner(offModel)
      // doesn't work
      //      // using Double.MaxValue causes panic in spinner's preferred-size
      //      ggOff.preferredSize = new Dimension(ggName.preferredSize.width, ggOff.preferredSize.height)
      //      ggOff.maximumSize   = ggOff.preferredSize
      val ggPlay    = new CheckBox

      val lbName  = new Label(       "Name:", EmptyIcon, Alignment.Right)
      val lbOff   = new Label( "Offset [s]:", EmptyIcon, Alignment.Right)
      val lbPlay  = new Label(    "Playing:", EmptyIcon, Alignment.Right)

      val box = new GroupPanel {
        horizontal  = Seq(Par(Trailing)(lbName, lbOff, lbPlay), Par(ggName , ggOff, ggPlay))
        vertical    = Seq(Par(Baseline)(lbName, ggName),
          Par(Baseline)(lbOff , ggOff ),
          Par(Baseline)(lbPlay, ggPlay))
      }

      val pane = desktop.OptionPane.confirmation(box, optionType = Dialog.Options.OkCancel,
        messageType = Dialog.Message.Question, focus = Some(ggName))
      pane.title  = s"New $prefix"
      val res = pane.show(window)

      if (res == Dialog.Result.Ok) {
        val name      = ggName.text
        val seconds   = offModel.getNumber.doubleValue()
        val offset    = (seconds * TimeRef.SampleRate + 0.5).toLong
        val playing   = ggPlay.selected
        val res = Config[S](name = name, offset = offset, playing = playing)
        ok(res)
      }
    }

    def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
      import config.name
      val folder    = _Folder[S] // XXX TODO - can we ask the user to pick one?
      val offset    = LongObj   .newVar(LongObj   .newConst[S](config.offset ))
      val playing   = BooleanObj.newVar(BooleanObj.newConst[S](config.playing))
      val obj      = _Ensemble[S](folder, offset, playing)
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _Ensemble[S]],
                                  var playing: _Boolean, val isEditable: Boolean)
      extends ListObjView /* .Ensemble */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.BooleanExprLike[S] {

      type E[~ <: stm.Sys[~]] = _Ensemble[~]

      def factory: ObjView.Factory = Ensemble

      def isViewable = true

      protected def exprValue: _Boolean = playing
      protected def exprValue_=(x: _Boolean): Unit = playing = x
      protected def expr(implicit tx: S#Tx): BooleanObj[S] = objH().playing

      def value: Any = ()

      def init(obj: _Ensemble[S])(implicit tx: S#Tx): this.type = {
        initAttrs(obj)
        disposables ::= obj.changed.react { implicit tx => upd =>
          upd.changes.foreach {
            case _Ensemble.Playing(ch) =>
              deferAndRepaint {
                playing = ch.now
              }

            case _ =>
          }
        }
        this
      }

      override def openView(parent: Option[Window[S]])
                           (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
        val ens   = objH()
        val w     = EnsembleFrame(ens)
        Some(w)
      }
    }
  }

  // -------- Nuages --------

  object Nuages extends ListObjView.Factory {
    type E[S <: stm.Sys[S]] = _Nuages[S]
    val icon          : Icon      = raphaelIcon(raphael.Shapes.CloudWhite)
    val prefix        : _String   = "Nuages"
    val humanName     : _String   = "Wolkenpumpe"
    def tpe           : Obj.Type  = _Nuages
    def category      : _String   = ObjView.categComposition
    def hasMakeDialog : Boolean   = true

    def mkListView[S <: Sys[S]](obj: _Nuages[S])(implicit tx: S#Tx): ListObjView[S] =
      new Nuages.Impl[S](tx.newHandle(obj)).initAttrs(obj)

    type Config[S <: stm.Sys[S]] = _String

    def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                   (ok: Config[S] => Unit)
                                   (implicit universe: Universe[S]): Unit = {
      val opt = OptionPane.textInput(message = s"Enter initial ${prefix.toLowerCase} name:",
        messageType = OptionPane.Message.Question, initial = prefix)
      opt.title = s"New $prefix"
      val res = opt.show(window)
      res.foreach(ok(_))
    }

    def makeObj[S <: Sys[S]](name: _String)(implicit tx: S#Tx): List[Obj[S]] = {
      val tl  = _Timeline[S]
      val obj = _Nuages[S](_Nuages.Surface.Timeline(tl))
      if (!name.isEmpty) obj.name = name
      obj :: Nil
    }

    final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, _Nuages[S]])
      extends ListObjView /* .Nuages */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.NonEditable[S]
      with ListObjViewImpl.EmptyRenderer[S] {

      type E[~ <: stm.Sys[~]] = _Nuages[~]

      def factory: ObjView.Factory = Nuages

      def isViewable = true

      def openView(parent: Option[Window[S]])
                  (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
        val frame = NuagesEditorFrameImpl(objH())
        Some(frame)
      }
    }
  }

  // -----------------------------

  def addObject[S <: Sys[S]](name: String, parent: _Folder[S], obj: Obj[S])
                            (implicit tx: S#Tx, cursor: stm.Cursor[S]): UndoableEdit = {
    // val parent = targetFolder
    // parent.addLast(obj)
    val idx = parent.size
//    implicit val folderSer = _Folder.serializer[S]
    EditFolderInsertObj[S](name, parent, idx, obj)
  }

  type PrimitiveConfig[A] = (String, A)

  /** Displays a simple new-object configuration dialog, prompting for a name and a value. */
  def primitiveConfig[S <: Sys[S], A](window: Option[desktop.Window], tpe: String, ggValue: Component,
                                      prepare: => Option[A]): Option[PrimitiveConfig[A]] = {
    val nameOpt = GUI.keyValueDialog(value = ggValue, title = s"New $tpe", defaultName = tpe, window = window)
    for {
      name  <- nameOpt
      value <- prepare
    } yield {
      (name, value)
    }
  }

  private[this] val colrIconDark = new AWTColor(200, 200, 200)

  final val IconExtent = 16

  def raphaelIcon(shape: Path2D => Unit): Icon = {
    val fill = if (Mellite.isDarkSkin) colrIconDark else AWTColor.black
    raphael.Icon(extent = IconExtent, fill = fill)(shape)
  }

  trait Impl[S <: stm.Sys[S]] extends ObjView[S] /* with ModelImpl[ObjView.Update[S]] */
    with ObservableImpl[S, ObjView.Update[S]] {

    override def toString = s"ElementView.${factory.prefix}(name = $name)"

    def objH: stm.Source[S#Tx, Obj[S]]

    def obj(implicit tx: S#Tx): Obj[S] = objH()

    /** Forwards to factory. */
    def humanName: String = factory.humanName

    /** Forwards to factory. */
    def icon: Icon = factory.icon

    var nameOption : Option[String] = None
    var colorOption: Option[_Color] = None

    protected var disposables: List[Disposable[S#Tx]] = Nil

    def dispose()(implicit tx: S#Tx): Unit = disposables.foreach(_.dispose())

    final protected def deferAndRepaint(body: => Unit)(implicit tx: S#Tx): Unit = {
      deferTx(body)
      fire(ObjView.Repaint(this))
    }

    /** Sets name and color. */
    def initAttrs(obj: Obj[S])(implicit tx: S#Tx): this.type = {
      val attr      = obj.attr

      val nameView  = CellView.attr[S, String, StringObj](attr, ObjKeys.attrName)
      disposables ::= nameView.react { implicit tx => opt =>
        deferAndRepaint {
          nameOption = opt
        }
      }
      nameOption   = nameView()

      val colorView = CellView.attr[S, _Color, _Color.Obj](attr, ObjView.attrColor)
      disposables ::= colorView.react { implicit tx => opt =>
        deferAndRepaint {
          colorOption = opt
        }
      }
      colorOption  = colorView()
      this
    }
  }

  trait ExprLike[S <: stm.Sys[S], A, Ex[~ <: stm.Sys[~]] <: Expr[~, A]] extends ObjView[S] {
    protected def expr(implicit tx: S#Tx): Ex[S]

    protected implicit def exprType: Type.Expr[A, Ex]

    // XXX TODO - this is a quick hack for demo
    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      universe.workspace match {
        case cf: Workspace.Confluent =>
          // XXX TODO - all this casting is horrible
          implicit val uni: Universe[Confluent] = universe.asInstanceOf[Universe[Confluent]]
          implicit val ctx: Confluent#Tx = tx.asInstanceOf[Confluent#Tx]
          implicit val ser: Serializer[Confluent#Tx, Access[Cf], Ex[Cf]] = exprType.serializer[Confluent]
          val name = CellView.name[Confluent](obj.asInstanceOf[Obj[Confluent]])
            .map(n => s"History for '$n'")
          val w = new WindowImpl[Confluent](name) {
            val view: UniverseView[Confluent] = ExprHistoryView[A, Ex](cf, expr.asInstanceOf[Ex[Confluent]])
            init()
          }
          Some(w.asInstanceOf[Window[S]])
        case _ => None
      }
    }
  }

  /** A trait that when mixed in provides `isViewable` and `openView` as non-op methods. */
  trait NonViewable[S <: stm.Sys[S]] extends ObjView[S] {

    def isViewable: _Boolean = false

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = None
  }
}
