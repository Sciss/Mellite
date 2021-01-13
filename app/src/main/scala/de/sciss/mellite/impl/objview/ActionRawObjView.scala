///*
// *  ActionObjView.scala
// *  (Mellite)
// *
// *  Copyright (c) 2012-2021 Hanns Holger Rutz. All rights reserved.
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
//import de.sciss.icons.raphael
//import de.sciss.lucre.expr.SpanLikeObj
//import de.sciss.lucre.{Txn => LTxn}
//import de.sciss.lucre.stm.Obj
//import de.sciss.lucre.swing.Window
//import de.sciss.lucre.synth.Txn
//import de.sciss.mellite.{CodeFrame, Mellite, ObjListView, ObjTimelineView, ObjView}
//import de.sciss.mellite.impl.ObjTimelineViewImpl
//import de.sciss.proc.Implicits._
//import de.sciss.proc.{ActionRaw, Universe}
//import javax.swing.Icon
//
//@deprecated("Action should be used instead of ActionRaw", since = "2.46.1")
//object ActionRawObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
//  type E[~ <: LTxn[~]] = ActionRaw[~] // .Elem[T]
//  val icon      : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.No)
//  val prefix    : String    = "ActionRaw"
//  def humanName : String    = s"$prefix (obsolete)"
//  def tpe       : Obj.Type  = ActionRaw
//  def category  : String    = ObjView.categMisc // categComposition
//
//  def mkListView[T <: Txn[T]](obj: ActionRaw[T])(implicit tx: T): ObjListView[T] =
//    new ListImpl(tx.newHandle(obj)).initAttrs(obj)
//
//  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
//    val obj = ActionRaw.Var(ActionRaw.empty[T])
//    obj.name = name
//    obj :: Nil
//  }
//
//  private trait Impl[T <: Txn[T]]
//    extends ObjListView /* .Action */[T]
//    with ObjViewImpl.Impl[T]
//    with ObjListViewImpl.NonEditable[T]
//    with ObjListViewImpl.EmptyRenderer[T]
//    with ActionRawObjView[T] {
//
//    override def objH: Source[T, ActionRaw[T]]
//
//    override def obj(implicit tx: T): ActionRaw[T] = objH()
//
//    final type E[~ <: LTxn[~]] = ActionRaw[~] // .Elem[~]
//
//    final def factory: ObjView.Factory = ActionRawObjView
//
//    final def isViewable = true
//
//    final def openView(parent: Option[Window[T]])
//                      (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
//      import Mellite.compiler
//      val frame = CodeFrame.actionRaw(obj)
//      Some(frame)
//    }
//  }
//
//  private final class ListImpl[T <: Txn[T]](val objH: Source[T, ActionRaw[T]])
//    extends Impl[T]
//
//  def mkTimelineView[T <: Txn[T]](id: Ident[T], span: SpanLikeObj[T], obj: ActionRaw[T],
//                                  context: ObjTimelineView.Context[T])(implicit tx: T): ObjTimelineView[T] = {
//    val res = new TimelineImpl[T](tx.newHandle(obj)).initAttrs(id, span, obj)
//    res
//  }
//
//  private final class TimelineImpl[T <: Txn[T]](val objH : Source[T, ActionRaw[T]])
//    extends Impl[T] with ObjTimelineViewImpl.HasMuteImpl[T]
//}
//trait ActionRawObjView[T <: LTxn[T]] extends ObjView[T] {
//  type Repr = ActionRaw[T]
//}