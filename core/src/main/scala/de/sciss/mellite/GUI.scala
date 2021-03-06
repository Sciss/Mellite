/*
 *  GUI.scala
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

package de.sciss.mellite

import de.sciss.audiowidgets.{ParamField, RotaryKnob, Transport}
import de.sciss.desktop.{KeyStrokes, OptionPane, Util}
import de.sciss.icons.raphael
import de.sciss.lucre.swing.LucreSwing.{defer, requireEDT}
import de.sciss.lucre.{Cursor, Txn}
import de.sciss.proc.SoundProcesses
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.GroupPanel
import de.sciss.{desktop, equal, numbers}

import java.awt.event.ActionEvent
import java.awt.geom.{AffineTransform, Area, Path2D}
import java.awt.image.BufferedImage
import java.awt.{BasicStroke, Color, Font, Graphics, Graphics2D, RenderingHints, Shape}
import javax.imageio.ImageIO
import javax.swing.{Icon, ImageIcon, KeyStroke, SwingUtilities, UIManager}
import scala.concurrent.{ExecutionContext, Future}
import scala.swing.Reactions.Reaction
import scala.swing.Swing._
import scala.swing.event.{Key, SelectionChanged, ValueChanged}
import scala.swing.{Action, Alignment, Button, Color, Component, Dialog, Dimension, Label, RootPanel, Slider, TextField}
import scala.util.{Failure, Success, Try}

object GUI {
  lazy val isDarkSkin: Boolean = UIManager.getBoolean("dark-skin")

  def keyStrokeText(ks: KeyStroke): String = {
    val s0  = ks.toString
    val i   = s0.indexOf("pressed ")
    if (i < 0) s0 else s0.substring(0, i) + s0.substring(i + 8)
  }

  def keyValueDialog(value: Component, title: String = "New Entry", defaultName: String = "Name",
                     window: Option[desktop.Window] = None): Option[String] = {
    val ggName  = new TextField(10)
    ggName.text = defaultName
    val lbName  = new Label( "Name:", EmptyIcon, Alignment.Right)
    val lbValue = new Label("Value:", EmptyIcon, Alignment.Right)

    val box = new GroupPanel {
      horizontal  = Seq(Par(Trailing)(lbName, lbValue), Par          (ggName , value))
      vertical    = Seq(Par(Baseline)(lbName, ggName ), Par(Baseline)(lbValue, value))
    }

    val pane = desktop.OptionPane.confirmation(box, optionType = Dialog.Options.OkCancel,
      messageType = Dialog.Message.Question, focus = Some(value))
    pane.title  = title
    val res = pane.show(window)
    import equal.Implicits._
    if (res === Dialog.Result.Ok) {
      val name    = ggName.text
      Some(name)
    } else {
      None
    }
  }

  val colorSuccess: Color = new Color(0x00, 0xC0, 0x00)
  val colorWarning: Color = new Color(0xFF, 0xC8, 0x00)
  val colorFailure: Color = new Color(0xFF, 0x30, 0x40)

  def iconNormal  (fun: Path2D => Unit): Icon = raphael.TexturedIcon        (20)(fun)
  def iconDisabled(fun: Path2D => Unit): Icon = raphael.TexturedDisabledIcon(20)(fun)

  def iconSuccess (fun: Path2D => Unit): Icon =
    raphael.Icon(extent = 20, fill = colorSuccess, shadow = raphael.WhiteShadow)(fun)

  def iconFailure (fun: Path2D => Unit): Icon =
    raphael.Icon(extent = 20, fill = colorFailure, shadow = raphael.WhiteShadow)(fun)

  private val sharpStrk       = new BasicStroke(1f)
  private val sharpShadowYOff = 1f

  private final class SharpIcon(extent: Int, scheme: Transport.ColorScheme, outShape: Shape,
                                inShape: Shape) extends Icon {
    def getIconWidth : Int = extent
    def getIconHeight: Int = extent

    def paintIcon(c: java.awt.Component, g: Graphics, x: Int, y: Int): Unit = {
      val g2 = g.asInstanceOf[Graphics2D]
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE )
      val atOrig = g2.getTransform
      g2.translate(x + 1, y + 1 + sharpShadowYOff)
      g2.setPaint(scheme.shadowPaint)
      g2.fill(outShape)
      g2.translate(0, -sharpShadowYOff)
      g2.setPaint(scheme.outlinePaint)
      g2.fill(outShape)
      g2.setPaint(scheme.fillPaint(extent.toFloat / 20))
      g2.fill(inShape)
      g2.setTransform(atOrig)
    }
  }

  /** No texture, for more finely drawn icons. */
  def sharpIcon(fun: Path2D => Unit, extent: Int = 20): Icon = {
    val in0 = new Path2D.Float()
    fun(in0)
    val scale = (extent - 1).toDouble / 32
    val in  = AffineTransform.getScaleInstance(scale, scale).createTransformedShape(in0)
    val out = new Area(sharpStrk.createStrokedShape(in))
    out.add(new Area(in))
    val scheme = if (isDarkSkin) Transport.LightScheme else Transport.DarkScheme
    new SharpIcon(extent = extent, scheme = scheme, out, in)
  }

  def toolButton(action: Action, iconFun: Path2D => Unit, tooltip: String = ""): Button = {
    val res           = new Button(action)
    res.peer.putClientProperty("styleId", "icon-space")
    res.icon          = iconNormal  (iconFun)
    res.disabledIcon  = iconDisabled(iconFun)
    if (tooltip.nonEmpty) res.tooltip = tooltip
    res
  }

  def viewButton(action: Action, tooltip: String = ""): Button = {
    val res = toolButton(action, raphael.Shapes.View, tooltip)
    Util.addGlobalKey(res, KeyStrokes.menu1 + Key.Enter)
    res
  }

  def attrButton(action: Action, tooltip: String = ""): Button = {
    val res = toolButton(action, raphael.Shapes.Wrench, tooltip)
    Util.addGlobalKey(res, KeyStrokes.menu1 + Key.Semicolon)
    res
  }

  def addButton(action: Action, tooltip: String = ""): Button = {
    val res = toolButton(action, raphael.Shapes.Plus, tooltip)
    Util.addGlobalKey(res, KeyStrokes.menu1 + Key.N)
    res
  }

  def removeButton(action: Action, tooltip: String = ""): Button = {
    val res = toolButton(action, raphael.Shapes.Minus, tooltip)
    Util.addGlobalKey(res, KeyStrokes.menu1 + Key.BackSpace)
    res
  }

  def duplicateButton(action: Action, tooltip: String = ""): Button = {
    val res = toolButton(action, raphael.Shapes.SplitArrows, tooltip)
    Util.addGlobalKey(res, KeyStrokes.menu1 + Key.D)
    res
  }

  def linkFormats[A](pf: ParamField[A]*): Unit = {
    val reaction: Reaction = {
      case SelectionChanged(f: ParamField[_]) =>
        val fmt0Opt = f.selectedFormat
        fmt0Opt.foreach { fmt0 =>
          val idx = f.formats.indexOf(fmt0)
          if (idx >= 0) {
            pf.foreach { f1 =>
              if (f1 != f) {
                val fmt1 = f1.formats
                if (fmt1.size > idx) {
                  f1.selectedFormat = Some(fmt1(idx))
                }
              }
            }
          }
        }
    }
    pf.foreach(_.subscribe(reaction))
    // ! https://github.com/scala/scala-swing/issues/42
    pf.head.peer.putClientProperty("mellite.reaction", reaction)
  }

  final val Default_VisualBoostMin  =   1.0
  final val Default_VisualBoostMax  = 512.0

  // if `init` it greater than or equal to `lo`, the rotary position is initialized and the function is called
  def boostRotaryR(lo: Double = Default_VisualBoostMin,
                   hi: Double = Default_VisualBoostMax,
                   init: Double = -1.0,
                   tooltip: String = "Sonogram Contrast")
                  (fun: Double => Unit): Slider = {
    import numbers.Implicits._
    val knob = new RotaryKnob {
      min       = 0
      max       = 64
      focusable = false
      if (init >= lo && init <= hi) {
        value = (init.expLin(lo, hi, 0, 64) + 0.5).toInt
        fun(init)
      } else {
        value = 0
      }

      listenTo(this)
      reactions += {
        case ValueChanged(_) =>
          val scaled = value.linExp(0, 64, lo, hi)
          fun(scaled)
      }
      preferredSize = new Dimension(33, 28)
      background    = null
      // paintTrack = false
    }
    knob.tooltip = tooltip
    desktop.Util.fixWidth(knob)
    knob
  }

  def step[T <: Txn[T]](title: String, message: String, window: Option[desktop.Window] = None,
                        timeout: Int = 1000)(fun: T => Unit)
                       (implicit cursor: Cursor[T]): Unit = {
    val f = atomic[T, Unit](title = title, message = message, window = window, timeout = timeout)(fun)
    import ExecutionContext.Implicits.global
    f.onComplete {
      case Failure(ex)  => SoundProcesses.errorHandler(title, ex)
      case _            =>
    }
  }

  def atomic[T <: Txn[T], A](title: String, message: String, window: Option[desktop.Window] = None,
                             timeout: Int = 1000)(fun: T => A)
                            (implicit cursor: Cursor[T]): Future[A] = {
    requireEDT()
    val res = SoundProcesses.atomic[T, A](fun)
    var opt: OptionPane[Unit] = null
    val t = new javax.swing.Timer(timeout, (_: ActionEvent) => {
      if (!res.isCompleted) {
        opt = OptionPane.message(message = s"$message…")
        opt.showNonModal(window, title)
      }
    })
    t.setRepeats(false)
    t.start()

    import ExecutionContext.Implicits.global

    res.onComplete { _ =>
      t.stop()
      defer {
        if (opt != null) {
          val w = SwingUtilities.getWindowAncestor(opt.peer)
          if (w != null) w.dispose()
        }
      }
    }
    res
  }

  def getImage(name: String): BufferedImage = {
    val is = GUI.getClass.getResourceAsStream(name)
    val image = if (is != null) {
      val res = ImageIO.read(is)
      is.close()
      res
    } else {
      val res = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
      val g2  = res.createGraphics()
      g2.setColor(Color.black)
      g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18))
      g2.drawString("?", 4, 16)
      g2.dispose()
      res
    }
    image
  }

  def getImageIcon(name: String): ImageIcon = {
    val image = getImage(s"icon-$name.png")
    new ImageIcon(image)
  }

  /** Interprets an option so that `Some(x)` is a `Success(x)`, and `None` means `Failure(Aborted())`. */
  def optionToAborted[A](opt: Option[A]): Try[A] = opt match {
    case Some(x)  => Success(x)
    case None     => Failure(Aborted())
  }

  /** Sets the location of a given window `w` relative to the on-screen location of visible component `c`.
    *
    * @param w        the window to position
    * @param c        the component relative to which to position the window
    * @param hAlign   one of `Alignment.Left`, `Alignment.Center`, `Alignment.Right`
    * @param vAlign   one of `Alignment.Top` , `Alignment.Center`, `Alignment.Bottom`
    * @param pad      additional spacing (only applies where an alignment is ''not'' `Center`)
    */
  def setLocationRelativeTo(w: RootPanel, c: Component,
                            hAlign: Alignment.Value = Alignment.Center, vAlign: Alignment.Value = Alignment.Center,
                            pad: Int = 0): Unit = {
    val wSize     = w.size
    val cWin      = SwingUtilities.getWindowAncestor(c.peer)
    if (cWin == null) return
    val gc        = cWin.getGraphicsConfiguration
    if (gc   == null) return
    val gcBounds  = gc.getBounds
    val cSize     = c.size
    val cLoc      = c.locationOnScreen

    import cLoc.{x => cx, y => cy}
    import cSize.{height => ch, width => cw}
    import gcBounds.{height => gh, width => gw, x => gx, y => gy}
    import wSize.{height => wh, width => ww}

    val x = hAlign match {
      case Alignment.Left   | Alignment.Leading   => cx - ww - pad
      case Alignment.Right  | Alignment.Trailing  => cx + cw + pad
      case _                                      => cx + (cw - ww)/2
    }

    val y = vAlign match {
      case Alignment.Top    | Alignment.Leading   => cy - wh - pad
      case Alignment.Bottom | Alignment.Trailing  => cy + ch + pad
      case _                                      => cy + (ch - wh)/2
    }

    // clip to screen area
    val yc = math.max(gy, math.min(y, gy + gh - wh))
    val xc = math.max(gx, math.min(x, gx + gw - ww))
    w.peer.setLocation(xc, yc)
  }

  /** Formats a sequence of strings as a table with a given number of columns.
    * Column width is made to match the longest string in the items, plus a given padding.
    * Each line is terminated with a newline character.
    */
  def formatTextTable(items: Seq[String], columns: Int, pad: Int = 2): String = {
    val colSize = if (items.isEmpty) 0 else items.iterator.map(_.length).max
    val sbSz    = ((colSize + pad) * columns + 1) * items.size
    val sb      = new StringBuilder(sbSz)
    items.grouped(columns).foreach { sq =>
      sq.foreach { item  =>
        val pad = " " * (colSize - item.length)
        sb.append("  ")
        sb.append(item)
        sb.append(pad)
      }
      sb.append('\n')
    }
    sb.result()
  }
}