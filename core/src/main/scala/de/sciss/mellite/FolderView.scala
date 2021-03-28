/*
 *  FolderView.scala
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

import java.net.URI

import de.sciss.desktop.UndoManager
import de.sciss.lucre.swing.{TreeTableView, View}
import de.sciss.lucre.{Folder, Obj, Source, Txn, synth}
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.model.Model
import de.sciss.proc.Universe

import scala.collection.immutable.{IndexedSeq => Vec}

object FolderView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[T <: synth.Txn[T]](root: Folder[T])
                                (implicit tx: T, universe: Universe[T], undoManager: UndoManager): FolderView[T]

    def cleanSelection[T <: Txn[T]](in: Selection[T]): Selection[T]
  }

  def apply[T <: synth.Txn[T]](root: Folder[T])
                         (implicit tx: T, universe: Universe[T], undoManager: UndoManager): FolderView[T] =
    companion(root)

  type NodeView[T <: Txn[T]] = TreeTableView.NodeView[T, Obj[T], Folder[T], ObjListView[T]]

  /** A selection is a sequence of paths, where a path is a prefix of folders and a trailing element.
    * The prefix is guaranteed to be non-empty.
    */
  // type Selection[T <: Txn[T]] = Vec[(Vec[ObjView.FolderLike[T]], ObjView[T])]
  type Selection[T <: Txn[T]] = List[NodeView[T]]

  /** Removes children from the selection whose parents are already included. */
  def cleanSelection[T <: Txn[T]](in: Selection[T]): Selection[T] = companion.cleanSelection(in)

  final case class SelectionDnDData[T <: Txn[T]](universe: Universe[T], selection: Selection[T]) {
    type T1 = T

    lazy val types: Set[Int] = selection.iterator.map(_.renderData.factory.tpe.typeId).toSet
  }

  // Document not serializable -- local JVM only DnD -- cf. stackoverflow #10484344
  val SelectionFlavor: Flavor[SelectionDnDData[_]] = DragAndDrop.internalFlavor

  sealed trait Update[T <: Txn[T]] { def view: FolderView[T] }
  final case class SelectionChanged[T <: Txn[T]](view: FolderView[T], selection: Selection[T])
    extends Update[T]
}
trait FolderView[T <: Txn[T]] extends Model[FolderView.Update[T]] with View.Editable[T] with UniverseObjView[T] {
  def selection: FolderView.Selection[T]

  def locations: Vec[ArtifactLocationObjView[T]]

  def insertionPoint(implicit tx: T): (Folder[T], Int)

  def findLocation(f: URI): Option[ActionArtifactLocation.QueryResult[T]]

  def root: Source[T, Folder[T]]

  override def obj(implicit tx: T): Folder[T]
}