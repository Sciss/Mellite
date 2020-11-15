///*
// *  EnsembleObjView.scala
// *  (Mellite)
// *
// *  Copyright (c) 2012-2020 Hanns Holger Rutz. All rights reserved.
// *
// *  This software is published under the GNU Affero General Public License v3+
// *
// *
// *  For further information, please contact Hanns Holger Rutz at
// *  contact@sciss.de
// */
//
//package de.sciss.mellite.impl.objview
//
//import de.sciss.desktop
//import de.sciss.icons.raphael
//import de.sciss.lucre.expr.{BooleanObj, LongObj}
//import de.sciss.lucre.{Txn => LTxn}
//import de.sciss.lucre.stm.{Folder, Obj}
//import de.sciss.lucre.swing.Window
//import de.sciss.lucre.synth.Txn
//import de.sciss.mellite.{ObjListView, ObjView}
//import de.sciss.mellite.impl.objview.ObjViewImpl.{TimeArg, raphaelIcon}
//import de.sciss.mellite.EnsembleFrame
//import de.sciss.mellite.impl.ObjViewCmdLineParser
//import de.sciss.processor.Processor.Aborted
//import de.sciss.swingplus.{GroupPanel, Spinner}
//import de.sciss.proc.Implicits._
//import de.sciss.proc.{Ensemble, TimeRef, Universe}
//import javax.swing.{Icon, SpinnerNumberModel}
//
//import scala.swing.Swing.EmptyIcon
//import scala.swing.{Alignment, CheckBox, Dialog, Label, TextField}
//import scala.util.{Failure, Success}
//
//@deprecated("Should only use Folder now with Control/Action", since = "2.46.1")
//object EnsembleObjView extends ObjListView.Factory {
//  type E[~ <: LTxn[~]] = Ensemble[~]
//  val icon          : Icon      = raphaelIcon(raphael.Shapes.No)
//  val prefix        : String    = "Ensemble"
//  def humanName     : String    = s"$prefix (obsolete)"
//  def tpe           : Obj.Type  = Ensemble
//  def category      : String    = ObjView.categMisc
//  def canMakeObj    : Boolean   = true
//
//  def mkListView[T <: Txn[T]](obj: Ensemble[T])(implicit tx: T): ObjListView[T] = {
//    val ens         = obj
//    val playingEx   = ens.playing
//    val playing     = playingEx.value
//    val isEditable  = playingEx match {
//      case BooleanObj.Var(_)  => true
//      case _            => false
//    }
//    new Impl[T](tx.newHandle(obj), playing = playing, isListCellEditable = isEditable).init(obj)
//  }
//
//  final case class Config[T <: LTxn[T]](name: String = prefix, offset: Long = 0L,
//                                           playing: Boolean = false, const: Boolean = false)
//
//  def initMakeDialog[T <: Txn[T]](window: Option[desktop.Window])
//                                 (done: MakeResult[T] => Unit)
//                                 (implicit universe: Universe[T]): Unit = {
//    val ggName    = new TextField(10)
//    ggName.text   = prefix
//    val offModel  = new SpinnerNumberModel(0.0, 0.0, 1.0e6 /* _Double.MaxValue */, 0.1)
//    val ggOff     = new Spinner(offModel)
//    // doesn't work
//    //      // using Double.MaxValue causes panic in spinner's preferred-size
//    //      ggOff.preferredSize = new Dimension(ggName.preferredSize.width, ggOff.preferredSize.height)
//    //      ggOff.maximumSize   = ggOff.preferredSize
//    val ggPlay    = new CheckBox
//
//    val lbName  = new Label(       "Name:", EmptyIcon, Alignment.Right)
//    val lbOff   = new Label( "Offset [s]:", EmptyIcon, Alignment.Right)
//    val lbPlay  = new Label(    "Playing:", EmptyIcon, Alignment.Right)
//
//    val box = new GroupPanel {
//      horizontal  = Seq(Par(Trailing)(lbName, lbOff, lbPlay), Par(ggName , ggOff, ggPlay))
//      vertical    = Seq(Par(Baseline)(lbName, ggName),
//        Par(Baseline)(lbOff , ggOff ),
//        Par(Baseline)(lbPlay, ggPlay))
//    }
//
//    val pane = desktop.OptionPane.confirmation(box, optionType = Dialog.Options.OkCancel,
//      messageType = Dialog.Message.Question, focus = Some(ggName))
//    pane.title  = s"New $prefix"
//    val res = pane.show(window)
//
//    val res1 = if (res == Dialog.Result.Ok) {
//      val name      = ggName.text
//      val seconds   = offModel.getNumber.doubleValue()
//      val offset    = (seconds * TimeRef.SampleRate + 0.5).toLong
//      val playing   = ggPlay.selected
//      Success(Config[T](name = name, offset = offset, playing = playing))
//    } else {
//      Failure(Aborted())
//    }
//    done(res1)
//  }
//
//  override def initMakeCmdLine[T <: Txn[T]](args: List[String])(implicit universe: Universe[T]): MakeResult[T] = {
//    object p extends ObjViewCmdLineParser[Config[T]](this, args) {
//      val playing : Opt[Boolean] = boolOpt(descr = "Initial playing value (0, 1, false, true, F, T)")
//      val offset  : Opt[TimeArg] = opt    (descr = "Offset value (frames, 1.3s, ...)",
//        default = Some(TimeArg.Frames(0L)))
//      val const   : Opt[Boolean] = opt    (descr = "Make constant offset instead of variable")
//
//      mainOptions = List(playing, offset)
//    }
//    p.parse(Config(name = p.name(), playing = p.playing(), const = p.const(), offset = p.offset().frames()))
//  }
//
//  def makeObj[T <: Txn[T]](config: Config[T])(implicit tx: T): List[Obj[T]] = {
//    import config.{const, name}
//    val folder    = Folder[T]() // XXX TODO - can we ask the user to pick one?
//    val offset0   = LongObj   .newConst[T](config.offset )
//    val offset    = if (const) offset0 else LongObj.newVar(offset0)
//    val playing   = BooleanObj.newVar(BooleanObj.newConst[T](config.playing))
//    val obj       = Ensemble[T](folder, offset, playing)
//    if (!name.isEmpty) obj.name = name
//    obj :: Nil
//  }
//
//  final class Impl[T <: Txn[T]](val objH: Source[T, Ensemble[T]],
//                                var playing: Boolean, val isListCellEditable: Boolean)
//    extends ObjListView /* .Ensemble */[T]
//      with ObjViewImpl.Impl[T]
//      with ObjListViewImpl.BooleanExprLike[T] {
//
//    override type Repr = Ensemble[T]
//
//    def factory: ObjView.Factory = EnsembleObjView
//
//    def isViewable = true
//
//    protected def exprValue: Boolean = playing
//    protected def exprValue_=(x: Boolean): Unit = playing = x
//    protected def expr(implicit tx: T): BooleanObj[T] = objH().playing
//
//    def value: Any = ()
//
//    def init(obj: Ensemble[T])(implicit tx: T): this.type = {
//      initAttrs(obj)
//      addDisposable(obj.changed.react { implicit tx =>upd =>
//        upd.changes.foreach {
//          case Ensemble.Playing(ch) =>
//            deferAndRepaint {
//              playing = ch.now
//            }
//
//          case _ =>
//        }
//      })
//      this
//    }
//
//    override def openView(parent: Option[Window[T]])
//                         (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
//      val ens   = objH()
//      val w     = EnsembleFrame(ens)
//      Some(w)
//    }
//  }
//}
