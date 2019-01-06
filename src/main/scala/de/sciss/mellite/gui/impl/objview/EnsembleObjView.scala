/*
 *  EnsembleObjView.scala
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

import de.sciss.desktop
import de.sciss.icons.raphael
import de.sciss.lucre.expr.{BooleanObj, LongObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Folder, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.ObjViewCmdLineParser
import de.sciss.mellite.gui.impl.objview.ObjViewImpl.{TimeArg, raphaelIcon}
import de.sciss.mellite.gui.{EnsembleFrame, ListObjView, ObjView}
import de.sciss.processor.Processor.Aborted
import de.sciss.swingplus.{GroupPanel, Spinner}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{Ensemble, TimeRef, Universe}
import javax.swing.{Icon, SpinnerNumberModel}

import scala.swing.Swing.EmptyIcon
import scala.swing.{Alignment, CheckBox, Dialog, Label, TextField}
import scala.util.{Failure, Success}

object EnsembleObjView extends ListObjView.Factory {
  type E[~ <: stm.Sys[~]] = Ensemble[~]
  val icon          : Icon      = raphaelIcon(raphael.Shapes.Cube2)
  val prefix        : String    = "Ensemble"
  def humanName     : String    = prefix
  def tpe           : Obj.Type  = Ensemble
  def category      : String    = ObjView.categComposition
  def canMakeObj    : Boolean   = true

  def mkListView[S <: Sys[S]](obj: Ensemble[S])(implicit tx: S#Tx): ListObjView[S] = {
    val ens         = obj
    val playingEx   = ens.playing
    val playing     = playingEx.value
    val isEditable  = playingEx match {
      case BooleanObj.Var(_)  => true
      case _            => false
    }
    new Impl[S](tx.newHandle(obj), playing = playing, isEditable = isEditable).init(obj)
  }

  final case class Config[S <: stm.Sys[S]](name: String = prefix, offset: Long = 0L,
                                           playing: Boolean = false, const: Boolean = false)

  def initMakeDialog[S <: Sys[S]](window: Option[desktop.Window])
                                 (done: MakeResult[S] => Unit)
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

    val res1 = if (res == Dialog.Result.Ok) {
      val name      = ggName.text
      val seconds   = offModel.getNumber.doubleValue()
      val offset    = (seconds * TimeRef.SampleRate + 0.5).toLong
      val playing   = ggPlay.selected
      Success(Config[S](name = name, offset = offset, playing = playing))
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

    opt[Boolean]('p', "playing")
      .text("Initial playing value (0, 1, false, true)")
      .action((v, c) => c.copy(playing = v))

    opt[TimeArg]('p', "playing")
      .text("Offset value (frames, 1.3s, ...)")
      .action((v, c) => c.copy(offset = v.frames()))

    opt[Unit]('c', "const")
      .text(s"Make constant offset instead of variable")
      .action((_, c) => c.copy(const = true))

    parseConfig(args, default)
  }

  def makeObj[S <: Sys[S]](config: Config[S])(implicit tx: S#Tx): List[Obj[S]] = {
    import config.{name, const}
    val folder    = Folder[S] // XXX TODO - can we ask the user to pick one?
    val offset0   = LongObj   .newConst[S](config.offset )
    val offset    = if (const) offset0 else LongObj.newVar(offset0)
    val playing   = BooleanObj.newVar(BooleanObj.newConst[S](config.playing))
    val obj       = Ensemble[S](folder, offset, playing)
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Ensemble[S]],
                                var playing: Boolean, val isEditable: Boolean)
    extends ListObjView /* .Ensemble */[S]
      with ObjViewImpl.Impl[S]
      with ListObjViewImpl.BooleanExprLike[S] {

    type E[~ <: stm.Sys[~]] = Ensemble[~]

    def factory: ObjView.Factory = EnsembleObjView

    def isViewable = true

    protected def exprValue: Boolean = playing
    protected def exprValue_=(x: Boolean): Unit = playing = x
    protected def expr(implicit tx: S#Tx): BooleanObj[S] = objH().playing

    def value: Any = ()

    def init(obj: Ensemble[S])(implicit tx: S#Tx): this.type = {
      initAttrs(obj)
      disposables ::= obj.changed.react { implicit tx => upd =>
        upd.changes.foreach {
          case Ensemble.Playing(ch) =>
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
