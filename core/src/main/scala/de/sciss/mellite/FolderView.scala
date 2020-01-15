/*
 *  FolderView.scala
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

package de.sciss.mellite

import java.io.File

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Folder, Obj, Sys}
import de.sciss.lucre.swing.{TreeTableView, View}
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.mellite.DragAndDrop.Flavor
import de.sciss.model.Model
import de.sciss.synth.proc.Universe

import scala.collection.immutable.{IndexedSeq => Vec}

object FolderView {
  private[mellite] var peer: Companion = _

  private def companion: Companion = {
    require (peer != null, "Companion not yet installed")
    peer
  }

  private[mellite] trait Companion {
    def apply[S <: SSys[S]](root: Folder[S])
                           (implicit tx: S#Tx, universe: Universe[S], undoManager: UndoManager): FolderView[S]

    def cleanSelection[S <: Sys[S]](in: Selection[S]): Selection[S]
  }

  def apply[S <: SSys[S]](root: Folder[S])
                         (implicit tx: S#Tx, universe: Universe[S], undoManager: UndoManager): FolderView[S] =
    companion(root)

  type NodeView[S <: Sys[S]] = TreeTableView.NodeView[S, Obj[S], Folder[S], ObjListView[S]]

  /** A selection is a sequence of paths, where a path is a prefix of folders and a trailing element.
    * The prefix is guaranteed to be non-empty.
    */
  // type Selection[S <: Sys[S]] = Vec[(Vec[ObjView.FolderLike[S]], ObjView[S])]
  type Selection[S <: Sys[S]] = List[NodeView[S]]

  /** Removes children from the selection whose parents are already included. */
  def cleanSelection[S <: Sys[S]](in: Selection[S]): Selection[S] = companion.cleanSelection(in)

  final case class SelectionDnDData[S <: Sys[S]](universe: Universe[S], selection: Selection[S]) {
    type S1 = S

    lazy val types: Set[Int] = selection.iterator.map(_.renderData.factory.tpe.typeId).toSet
  }

  // Document not serializable -- local JVM only DnD -- cf. stackoverflow #10484344
  val SelectionFlavor: Flavor[SelectionDnDData[_]] = DragAndDrop.internalFlavor

  sealed trait Update[S <: Sys[S]] { def view: FolderView[S] }
  final case class SelectionChanged[S <: Sys[S]](view: FolderView[S], selection: Selection[S])
    extends Update[S]
}
trait FolderView[S <: Sys[S]] extends Model[FolderView.Update[S]] with View.Editable[S] with UniverseView[S] {
  def selection: FolderView.Selection[S]

  def locations: Vec[ArtifactLocationObjView[S]]

  def insertionPoint(implicit tx: S#Tx): (Folder[S], Int)

  def findLocation(f: File): Option[ActionArtifactLocation.QueryResult[S]]

  def root: stm.Source[S#Tx, Folder[S]]
}