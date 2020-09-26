/*
 *  FolderObjView.scala
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

import de.sciss.lucre.expr.CellView
import de.sciss.lucre.{Folder, Obj, Source, Txn => LTxn}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Txn
import de.sciss.mellite.{ObjListView, ObjView}
import de.sciss.mellite.FolderFrame
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe
import javax.swing.{Icon, UIManager}

object FolderObjView extends NoArgsListObjViewFactory {
  type E[~ <: LTxn[~]] = Folder[~]
  def icon          : Icon      = UIManager.getIcon("Tree.openIcon")  // Swing.EmptyIcon
  val prefix        : String   = "Folder"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = Folder
  def category      : String   = ObjView.categOrganization

  def mkListView[T <: Txn[T]](obj: Folder[T])(implicit tx: T): ObjListView[T] =
    new Impl[T](tx.newHandle(obj)).initAttrs(obj)

  def makeObj[T <: Txn[T]](name: String)(implicit tx: T): List[Obj[T]] = {
    val obj  = Folder[T]()
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO: could be viewed as a new folder view with this folder as root
  final class Impl[T <: Txn[T]](val objH: Source[T, Folder[T]])
    extends ObjListView /* .Folder */[T]
      with ObjViewImpl.Impl[T]
      with ObjListViewImpl.EmptyRenderer[T]
      with ObjListViewImpl.NonEditable[T] {

    type Repr = Folder[T]

    def factory: ObjView.Factory = FolderObjView

    def isViewable = true

    def openView(parent: Option[Window[T]])
                (implicit tx: T, universe: Universe[T]): Option[Window[T]] = {
      val folderObj = objH()
      val nameView  = CellView.name(folderObj)
      Some(FolderFrame(nameView, folderObj))
    }
  }
}
