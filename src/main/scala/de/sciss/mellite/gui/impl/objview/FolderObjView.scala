/*
 *  FolderObjView.scala
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

import de.sciss.lucre.expr.CellView
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Folder, Obj}
import de.sciss.lucre.swing.Window
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.{FolderFrame, ObjListView, ObjView}
import de.sciss.synth.proc.Implicits._
import de.sciss.synth.proc.Universe
import javax.swing.{Icon, UIManager}

object FolderObjView extends NoArgsListObjViewFactory {
  type E[~ <: stm.Sys[~]] = Folder[~]
  def icon          : Icon      = UIManager.getIcon("Tree.openIcon")  // Swing.EmptyIcon
  val prefix        : String   = "Folder"
  def humanName     : String   = prefix
  def tpe           : Obj.Type  = Folder
  def category      : String   = ObjView.categOrganisation

  def mkListView[S <: Sys[S]](obj: Folder[S])(implicit tx: S#Tx): ObjListView[S] =
    new Impl[S](tx.newHandle(obj)).initAttrs(obj)

  def makeObj[S <: Sys[S]](name: String)(implicit tx: S#Tx): List[Obj[S]] = {
    val obj  = Folder[S]
    if (!name.isEmpty) obj.name = name
    obj :: Nil
  }

  // XXX TODO: could be viewed as a new folder view with this folder as root
  final class Impl[S <: Sys[S]](val objH: stm.Source[S#Tx, Folder[S]])
    extends ObjListView /* .Folder */[S]
      with ObjViewImpl.Impl[S]
      with ObjListViewImpl.EmptyRenderer[S]
      with ObjListViewImpl.NonEditable[S] {

    type Repr = Folder[S]

    def factory: ObjView.Factory = FolderObjView

    def isViewable = true

    def openView(parent: Option[Window[S]])
                (implicit tx: S#Tx, universe: Universe[S]): Option[Window[S]] = {
      val folderObj = objH()
      val nameView  = CellView.name(folderObj)
      Some(FolderFrame(nameView, folderObj))
    }
  }
}
