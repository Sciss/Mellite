/*
 *  ProcObjView.scala
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

package de.sciss.mellite.impl.proc

import de.sciss.icons.raphael
import de.sciss.lucre.synth.Txn
import de.sciss.lucre.{Cursor, Disposable, Ident, IntObj, Obj, Source, SpanLikeObj, TxnLike, Txn => LTxn}
import de.sciss.mellite
import de.sciss.mellite.impl.objview.{NoArgsListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.{ObjListView, ObjTimelineView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{ObjKeys, Proc}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object ProcObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: LTxn[~]] = Proc[~]

  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Cogs)
  val prefix        : String    = "Proc"
  val humanName     : String    = "Process"
  def tpe           : Obj.Type  = Proc
  def category      : String    = ObjView.categComposition

  def mkListView[T <: Txn[T]](obj: Proc[T])(implicit tx: T): ProcObjView[T] with ObjListView[T] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj  = Proc[T]()
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

//  type LinkMap[T <: LTxn[T]] = Map[String, Vec[ProcObjView.Link[T]]]
//  type ProcMap[T <: LTxn[T]] = IdentMap[Ident[T], T, ProcObjView[T]]
//  type ScanMap[T <: LTxn[T]] = IdentMap[Ident[T], T, (String, Source[T, Ident[T]])]

  type SelectionModel[T <: Txn[T]] = mellite.SelectionModel[T, ProcObjView[T]]

  /** Constructs a new proc view from a given proc, and a map with the known proc (views).
    * This will automatically add the new view to the map!
    */
  def mkTimelineView[T <: Txn[T]](timedId: Ident[T], span: SpanLikeObj[T], obj: Proc[T],
                                  context: ObjTimelineView.Context[T])(implicit tx: T): ProcObjView.Timeline[T] = {
    val attr = obj.attr
    val bus  = attr.$[IntObj](ObjKeys.attrBus    ).map(_.value)
    new ProcObjTimelineViewImpl[T](tx.newHandle(obj), busOption = bus, context = context)
      .init(timedId, span, obj)
  }

  // -------- Proc --------

  private final class ListImpl[T <: Txn[T]](val objH: Source[T, Proc[T]])
    extends ProcObjViewImpl[T]

  trait LinkTarget[T <: LTxn[T]] {
    def attr: InputAttr[T]
    def remove()(implicit tx: T, cursor: Cursor[T]): Option[UndoableEdit]
  }

  trait InputAttr[T <: LTxn[T]] extends Disposable[T] {
    def parent: ProcObjView.Timeline[T]
    def key: String
  }

//  final case class Link[T <: LTxn[T]](target: ProcObjView.Timeline[T], targetKey: String)

  /** A data set for graphical display of a proc. Accessors and mutators should
    * only be called on the event dispatch thread. Mutators are plain variables
    * and do not affect the underlying model. They should typically only be called
    * in response to observing a change in the model.
    */
  trait Timeline[T <: LTxn[T]]
    extends ProcObjView[T] with ObjTimelineView[T]
    with ObjTimelineView.HasMute
    with ObjTimelineView.HasGain
    with ObjTimelineView.HasFade {

    /** Convenience check for `span == Span.All` */
    def isGlobal: Boolean

    def context: ObjTimelineView.Context[T]

    def fireRepaint()(implicit tx: T): Unit

    var busOption: Option[Int]

    def debugString: String

    def px: Int
    def py: Int
    def pw: Int
    def ph: Int

    def pStart: Long
    def pStop : Long

    def addTarget   (tgt: LinkTarget[T])(implicit tx: TxnLike): Unit
    def removeTarget(tgt: LinkTarget[T])(implicit tx: TxnLike): Unit

    def targets(implicit tx: TxnLike): Set[LinkTarget[T]]
  }
}
trait ProcObjView[T <: LTxn[T]] extends ObjView[T] {
  type Repr = Proc[T]
}