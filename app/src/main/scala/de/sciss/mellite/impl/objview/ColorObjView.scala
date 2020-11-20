/*
 *  ColorObjView.scala
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

package de.sciss.mellite.impl.objview

import de.sciss.icons.raphael
import de.sciss.lucre.expr.CellView
import de.sciss.lucre.{Expr, Obj, Source, Txn => LTxn}
import de.sciss.lucre.swing.edit.EditVar
import de.sciss.lucre.swing.{View, Window}
import de.sciss.lucre.swing.LucreSwing.deferTx
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{MessageException, ObjListView, ObjView, UniverseView}
import de.sciss.mellite.impl.component.PaintIcon
import de.sciss.mellite.impl.objview.ObjViewImpl.{primitiveConfig, raphaelIcon}
import de.sciss.mellite.impl.{ObjViewCmdLineParser, WindowImpl}
import de.sciss.{desktop, numbers}
import de.sciss.proc.Implicits._
import de.sciss.proc.{Color, Universe}
import javax.swing.Icon
import org.rogach.scallop

import scala.swing.{Action, BorderPanel, Button, ColorChooser, Component, FlowPanel, GridPanel, Label, Swing}
import scala.util.{Failure, Success, Try}

object ColorObjView extends ObjListView.Factory {
  type E[~ <: LTxn[~]] = Color.Obj[~]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Paint)
  val prefix        : String   = "Color"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = Color.Obj
  def category      : String   = ObjView.categOrganization
  def canMakeObj    : Boolean   = true

  def mkListView[T <: Txn[T]](obj: Color.Obj[T])(implicit tx: T): ObjListView[T] = {
    val ex          = obj
    val value       = ex.value
    val isEditable  = ex match {
      case Color.Obj.Var(_)  => true
      case _                  => false
    }
    new Impl[T](tx.newHandle(obj), value, isEditable0 = isEditable).init(obj)
  }

  final case class Config[T <: LTxn[T]](name: String = prefix, value: Color, const: Boolean = false)

  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
                                 (done: MakeResult[T] => Unit)
                                 (implicit universe: Universe[T]): Unit = {
    val (ggValue, ggChooser) = mkColorEditor()
    val res0 = primitiveConfig[T, Color](window, tpe = prefix, ggValue = ggValue, prepare =
      Success(fromAWT(ggChooser.color)))
    val res = res0.map(c => Config[T](name = c.name, value = c.value))
    done(res)
  }

  private def mkColorEditor(): (Component, ColorChooser) = {
    val chooser = new ColorChooser()
    val bPredef = Color.Palette.map { colr =>
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

  def toAWT(c: Color): java.awt.Color = new java.awt.Color(c.rgba)
  def fromAWT(c: java.awt.Color): Color = {
    val rgba = c.getRGB
    Color.Palette.find(_.rgba == rgba).getOrElse(Color.User(rgba))
  }

  private implicit val ReadColor: scallop.ValueConverter[Color] = scallop.singleArgConverter { s =>
    parseString(s).get
  }

  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
      val const: Opt[Boolean] = opt     (descr = s"Make constant instead of variable")
      val value: Opt[Color]   = trailArg(
        descr = s"Initial color value (0-${Color.Palette.size - 1}, #rrggbb, red, rgb(), hsl(), ...)")
    }
    p.parse(Config(name = p.name(), value = p.value(), const = p.const()))
  }

  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
    import config._
    val obj0  = Color.Obj.newConst[T](value)
    val obj   = if (const) obj0 else Color.Obj.newVar(obj0)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  private lazy val cssPredefined: Map[String, Int] = Map(
    "AliceBlue"         -> 0xF0F8FF, "AntiqueWhite"      -> 0xFAEBD7, "Aqua"              -> 0x00FFFF,
    "Aquamarine"        -> 0x7FFFD4, "Azure"             -> 0xF0FFFF, "Beige"             -> 0xF5F5DC,
    "Bisque"            -> 0xFFE4C4, "Black"             -> 0x000000, "BlanchedAlmond"    -> 0xFFEBCD,
    "Blue"              -> 0x0000FF, "BlueViolet"        -> 0x8A2BE2, "Brown"             -> 0xA52A2A,
    "BurlyWood"         -> 0xDEB887, "CadetBlue"         -> 0x5F9EA0, "Chartreuse"        -> 0x7FFF00,
    "Chocolate"         -> 0xD2691E, "Coral"             -> 0xFF7F50, "CornflowerBlue"    -> 0x6495ED,
    "Cornsilk"          -> 0xFFF8DC, "Crimson"           -> 0xDC143C, "Cyan"  	          -> 0x00FFFF,
    "DarkBlue"          -> 0x00008B, "DarkCyan"          -> 0x008B8B, "DarkGoldenRod"     -> 0xB8860B,
    "DarkGray"          -> 0xA9A9A9, "DarkGrey"          -> 0xA9A9A9, "DarkGreen"         -> 0x006400,
    "DarkKhaki"         -> 0xBDB76B, "DarkMagenta"       -> 0x8B008B, "DarkOliveGreen"    -> 0x556B2F,
    "DarkOrange"        -> 0xFF8C00, "DarkOrchid"        -> 0x9932CC, "DarkRed"           -> 0x8B0000,
    "DarkSalmon"        -> 0xE9967A, "DarkSeaGreen"      -> 0x8FBC8F, "DarkSlateBlue"     -> 0x483D8B,
    "DarkSlateGray"     -> 0x2F4F4F, "DarkSlateGrey"     -> 0x2F4F4F, "DarkTurquoise"     -> 0x00CED1,
    "DarkViolet"        -> 0x9400D3, "DeepPink"          -> 0xFF1493, "DeepSkyBlue"       -> 0x00BFFF,
    "DimGray"           -> 0x696969, "DimGrey"           -> 0x696969, "DodgerBlue"        -> 0x1E90FF,
    "FireBrick"         -> 0xB22222, "FloralWhite"       -> 0xFFFAF0, "ForestGreen"       -> 0x228B22,
    "Fuchsia"           -> 0xFF00FF, "Gainsboro"         -> 0xDCDCDC, "GhostWhite"        -> 0xF8F8FF,
    "Gold"              -> 0xFFD700, "GoldenRod"         -> 0xDAA520, "Gray"              -> 0x808080,
    "Grey"              -> 0x808080, "Green"             -> 0x008000, "GreenYellow"       -> 0xADFF2F,
    "HoneyDew"          -> 0xF0FFF0, "HotPink"           -> 0xFF69B4, "IndianRed"         -> 0xCD5C5C,
    "Indigo"            -> 0x4B0082, "Ivory"             -> 0xFFFFF0, "Khaki"             -> 0xF0E68C,
    "Lavender"          -> 0xE6E6FA, "LavenderBlush"     -> 0xFFF0F5, "LawnGreen"         -> 0x7CFC00,
    "LemonChiffon"      -> 0xFFFACD, "LightBlue"         -> 0xADD8E6, "LightCoral"        -> 0xF08080,
    "LightCyan"         -> 0xE0FFFF, "LightGoldenRodYellow" -> 0xFAFAD2, "LightGray"      -> 0xD3D3D3,
    "LightGrey"         -> 0xD3D3D3, "LightGreen"        -> 0x90EE90, "LightPink"         -> 0xFFB6C1,
    "LightSalmon"       -> 0xFFA07A, "LightSeaGreen"     -> 0x20B2AA, "LightSkyBlue"      -> 0x87CEFA,
    "LightSlateGray"    -> 0x778899, "LightSlateGrey"    -> 0x778899, "LightSteelBlue"    -> 0xB0C4DE,
    "LightYellow"       -> 0xFFFFE0, "Lime"              -> 0x00FF00, "LimeGreen"         -> 0x32CD32,
    "Linen"             -> 0xFAF0E6, "Magenta"           -> 0xFF00FF, "Maroon"            -> 0x800000,
    "MediumAquaMarine"  -> 0x66CDAA, "MediumBlue"        -> 0x0000CD, "MediumOrchid"      -> 0xBA55D3,
    "MediumPurple"      -> 0x9370DB, "MediumSeaGreen"    -> 0x3CB371, "MediumSlateBlue"   -> 0x7B68EE,
    "MediumSpringGreen" -> 0x00FA9A, "MediumTurquoise"   -> 0x48D1CC, "MediumVioletRed"   -> 0xC71585,
    "MidnightBlue"      -> 0x191970, "MintCream"         -> 0xF5FFFA, "MistyRose"         -> 0xFFE4E1,
    "Moccasin"          -> 0xFFE4B5, "NavajoWhite"       -> 0xFFDEAD, "Navy"              -> 0x000080,
    "OldLace"           -> 0xFDF5E6, "Olive"             -> 0x808000, "OliveDrab"         -> 0x6B8E23,
    "Orange"            -> 0xFFA500, "OrangeRed"         -> 0xFF4500, "Orchid"            -> 0xDA70D6,
    "PaleGoldenRod"     -> 0xEEE8AA, "PaleGreen"         -> 0x98FB98, "PaleTurquoise"     -> 0xAFEEEE,
    "PaleVioletRed"     -> 0xDB7093, "PapayaWhip"        -> 0xFFEFD5, "PeachPuff"         -> 0xFFDAB9,
    "Peru"              -> 0xCD853F, "Pink"              -> 0xFFC0CB, "Plum"              -> 0xDDA0DD,
    "PowderBlue"        -> 0xB0E0E6, "Purple"            -> 0x800080, "RebeccaPurple"     -> 0x663399,
    "Red"               -> 0xFF0000, "RosyBrown"         -> 0xBC8F8F, "RoyalBlue"         -> 0x4169E1,
    "SaddleBrown"       -> 0x8B4513, "Salmon"            -> 0xFA8072, "SandyBrown"        -> 0xF4A460,
    "SeaGreen"          -> 0x2E8B57, "SeaShell"          -> 0xFFF5EE, "Sienna"            -> 0xA0522D,
    "Silver"            -> 0xC0C0C0, "SkyBlue"           -> 0x87CEEB, "SlateBlue"         -> 0x6A5ACD,
    "SlateGray"         -> 0x708090, "SlateGrey"         -> 0x708090, "Snow"              -> 0xFFFAFA,
    "SpringGreen"       -> 0x00FF7F, "SteelBlue"         -> 0x4682B4, "Tan"               -> 0xD2B48C,
    "Teal"              -> 0x008080, "Thistle"           -> 0xD8BFD8, "Tomato"            -> 0xFF6347,
    "Turquoise"         -> 0x40E0D0, "Violet"            -> 0xEE82EE, "Wheat"             -> 0xF5DEB3,
    "White"             -> 0xFFFFFF, "WhiteSmoke"        -> 0xF5F5F5, "Yellow"            -> 0xFFFF00,
    "YellowGreen"       -> 0x9ACD32
  ) .map { case (k, v) => (k.toLowerCase, v) }

  // supports some of the CSS syntax,
  // see https://www.w3schools.com/CSSref/css_colors_legal.asp
  private def parseString(s: String): Try[Color] = {
    def fail(msg: String) =
      Failure(MessageException(msg))

    val t     = s.trim.toLowerCase
    val i     = t.indexOf('(')
    val isFun = i >= 0
    if (isFun) {
      val j = t.indexOf(')')
      if (j != t.length - 1) fail(s"Unbalanced parentheses")
      else {
        val args: Array[String] = t.substring(i + 1, j).split(",").map(_.trim)
        val numArgs = args.length
        val funName = t.substring(0, i).trim

        def requireNumArgs(exp: Int)(body: => Try[Color]): Try[Color] =
          if (numArgs == exp) body else
            fail(s"Color function '$funName' requires $exp arguments, not $numArgs")

        import numbers.Implicits._

        def parseIntPercent(x: String): Try[Int] = Try {
          if (x.endsWith("%")) {
            x.substring(0, x.length - 1).toInt.clip(0, 100).linLin(0, 100, 0, 255).toInt
          } else {
            x.toInt.clip(0, 255)
          }
        }

        def parseFraction(x: String): Try[Int] = Try {
          (x.toDouble.clip(0, 1) * 255).toInt
        }

        def parseHue(x: String): Try[Float] = Try {
          x.toInt.wrap(0, 360) / 360f
        }

        def parsePercent(x: String): Try[Float] = Try {
          x.substring(0, x.length - 1).toInt.clip(0, 100) / 100f
        }

        funName match {
          case "rgb"  => requireNumArgs(3) {
              for {
                r <- parseIntPercent(args(0))
                g <- parseIntPercent(args(1))
                b <- parseIntPercent(args(2))
              } yield Color.User(0xFF000000 | (r << 16) | (g << 8) | b)
            }
          case "rgba" => requireNumArgs(4) {
            for {
              r <- parseIntPercent(args(0))
              g <- parseIntPercent(args(1))
              b <- parseIntPercent(args(2))
              a <- parseFraction  (args(3))
            } yield Color.User((a << 24) | (r << 16) | (g << 8) | b)
          }
          case "hsl"  => requireNumArgs(3) {
            for {
              h <- parseHue       (args(0))
              s <- parsePercent   (args(1))
              b <- parsePercent   (args(2))
            } yield {
              val awt = java.awt.Color.getHSBColor(h, s, b)
              fromAWT(awt)
            }
          }
          case "hsla" => requireNumArgs(4) {
            for {
              h <- parseHue       (args(0))
              s <- parsePercent   (args(1))
              b <- parsePercent   (args(2))
              a <- parseFraction  (args(3))
            } yield {
              val awt0  = java.awt.Color.getHSBColor(h, s, b)
              val rgba  = (awt0.getRGB & 0x00FFFFFF) | (a << 24)
              Color.User(rgba)
            }
          }
          case _ =>
            fail(s"Unsupported color function '$funName'")
        }
      }

    } else if (t.startsWith("#")) {  // hex
      Try(java.lang.Integer.parseInt(t.substring(1), 16)).map(hex =>
        Color.User(0xFF000000 | hex)
      )

    } else if (t.length > 0 && t.charAt(0).isDigit) { // predefined
      Try(t.toInt).flatMap { id =>
        if (id >= 0 && id < Color.Palette.size) {
          Success(Color.Palette(id))
        } else {
          fail(s"Unknown color name '$t'")
        }
      }
    } else {  // assume predefined CSS name
      cssPredefined.get(t) match {
        case Some(hex)  => Success(Color.User(0xFF000000 | hex))
        case None       => fail(s"Unknown color name '$t'")
      }
    }
  }

  final class Impl[T <: Txn[T]](val objH: Source[T, Color.Obj[T]],
                                var value: Color, isEditable0: Boolean)
    extends ObjListView /* .Color */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.SimpleExpr[T, Color, Color.Obj] { listView =>

    type Repr = Color.Obj[T]

    def isListCellEditable = false    // not until we have proper editing components

    def factory: ObjView.Factory = ColorObjView

    def exprType: Expr.Type[Color, Color.Obj] = Color.Obj

    def expr(implicit tx: T): Color.Obj[T] = objH()

    def configureListCellRenderer(label: Label): Component = {
      // renderers are used for "stamping", so we can reuse a single object.
      label.icon = ListIcon
      ListIcon.paint = toAWT(value)
      label
    }

    def convertEditValue(v: Any): Option[Color] = v match {
      case c: Color  => Some(c)
      case _          => None
    }

    def isViewable: Boolean = isEditable0

    override def openView(parent: Option[Window[T]])
                         (implicit tx: T, _universe: Universe[T]): Option[Window[T]] = {
      //        val opt = OptionPane.confirmation(message = component, optionType = OptionPane.Options.OkCancel,
      //          messageType = OptionPane.Message.Plain)
      //        opt.show(parent) === OptionPane.Result.Ok
      val title = CellView.name(obj)
      val w: WindowImpl[T] = new WindowImpl[T](title) { self =>
        val view: UniverseView[T] = new UniverseView[T] with ComponentHolder[Component] {
          type C = Component
          val universe: Universe[T] = _universe

          deferTx {
            val (compColor, chooser) = mkColorEditor()
            chooser.color = toAWT(value)
            val ggCancel = Button("Cancel") {
              closeMe() // self.handleClose()
            }

            def apply(): Unit = {
              val colr = fromAWT(chooser.color)
//              import universe.cursor
              val editOpt = cursor.step { implicit tx =>
                objH() match {
                  case Color.Obj.Var(vr) =>
                    Some(EditVar.Expr[T, Color, Color.Obj]("Change Color", vr, Color.Obj.newConst[T](colr)))
                  case _ => None
                }
              }
              editOpt.foreach { edit =>
                parent.foreach { p =>
                  p.view match {
                    case e: View.Editable[T] => e.undoManager.add(edit)
                    case _ => ()
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

            component = pane
          }

          def dispose()(implicit tx: T): Unit = ()
        }

        def closeMe(): Unit = {
          import view.universe.cursor
          cursor.step { implicit tx => self.dispose() }
        }

        init()
      }
      Some(w)
    }
  }

  private val ListIcon = new PaintIcon(java.awt.Color.black, 48, 16)
}

