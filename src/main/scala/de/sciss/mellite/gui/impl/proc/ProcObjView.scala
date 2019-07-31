/*
 *  ProcObjView.scala
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

package de.sciss.mellite.gui.impl.proc

import de.sciss.icons.raphael
import de.sciss.lucre.expr.{IntObj, SpanLikeObj}
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj, TxnLike}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.{ObjView, gui}
import de.sciss.mellite.gui.impl.objview.{NoArgsListObjViewFactory, ObjViewImpl}
import de.sciss.mellite.gui.{ObjListView, ObjTimelineView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.{ObjKeys, Proc}
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

object ProcObjView extends NoArgsListObjViewFactory with ObjTimelineView.Factory {
  type E[~ <: stm.Sys[~]] = Proc[~]

  val icon          : Icon      = ObjViewImpl.raphaelIcon(raphael.Shapes.Cogs)
  val prefix        : String    = "Proc"
  val humanName     : String    = "Process"
  def tpe           : Obj.Type  = Proc
  def category      : String    = ObjView.categComposition

  def mkListView[S <: Sys[S]](obj: Proc[S])(implicit tx: S#Tx): ProcObjView[S] with ObjListView[S] =
    new ListImpl(tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Proc[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

//  type LinkMap[S <: stm.Sys[S]] = Map[String, Vec[ProcObjView.Link[S]]]
//  type ProcMap[S <: stm.Sys[S]] = IdentifierMap[S#Id, S#Tx, ProcObjView[S]]
//  type ScanMap[S <: stm.Sys[S]] = IdentifierMap[S#Id, S#Tx, (String, stm.Source[S#Tx, S#Id])]

  type SelectionModel[S <: Sys[S]] = gui.SelectionModel[S, ProcObjView[S]]

  /** Constructs a new proc view from a given proc, and a map with the known proc (views).
    * This will automatically add the new view to the map!
    */
  def mkTimelineView[S <: Sys[S]](timedId: S#Id, span: SpanLikeObj[S], obj: Proc[S],
                                  context: ObjTimelineView.Context[S])(implicit tx: S#Tx): ProcObjView.Timeline[S] = {
    val attr = obj.attr
    val bus  = attr.$[IntObj](ObjKeys.attrBus    ).map(_.value)
    new ProcObjTimelineViewImpl[S](tx.newHandle(obj), busOption = bus, context = context)
      .init(timedId, span, obj)
  }

  // -------- Proc --------

  private final class ListImpl[S <: Sys[S]](val objH: stm.Source[S#Tx, Proc[S]])
    extends ProcObjViewImpl[S]

  trait LinkTarget[S <: stm.Sys[S]] {
    def attr: InputAttr[S]
    def remove()(implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit]
  }

  trait InputAttr[S <: stm.Sys[S]] extends Disposable[S#Tx] {
    def parent: ProcObjView.Timeline[S]
    def key: String
  }

//  final case class Link[S <: stm.Sys[S]](target: ProcObjView.Timeline[S], targetKey: String)

  /** A data set for graphical display of a proc. Accessors and mutators should
    * only be called on the event dispatch thread. Mutators are plain variables
    * and do not affect the underlying model. They should typically only be called
    * in response to observing a change in the model.
    */
  trait Timeline[S <: stm.Sys[S]]
    extends ProcObjView[S] with ObjTimelineView[S]
    with ObjTimelineView.HasMute
    with ObjTimelineView.HasGain
    with ObjTimelineView.HasFade {

    /** Convenience check for `span == Span.All` */
    def isGlobal: Boolean

    def context: ObjTimelineView.Context[S]

    def fireRepaint()(implicit tx: S#Tx): Unit

    var busOption: Option[Int]

    def debugString: String

    def px: Int
    def py: Int
    def pw: Int
    def ph: Int

    def pStart: Long
    def pStop : Long

    def addTarget   (tgt: LinkTarget[S])(implicit tx: TxnLike): Unit
    def removeTarget(tgt: LinkTarget[S])(implicit tx: TxnLike): Unit

    def targets(implicit tx: TxnLike): Set[LinkTarget[S]]
  }
}
trait ProcObjView[S <: stm.Sys[S]] extends ObjView[S] {
  type Repr = Proc[S]
}