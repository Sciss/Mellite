/*
 *  BasicCollectionTool.scala
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

package de.sciss.mellite.impl.tool

import java.awt.event.MouseEvent

import de.sciss.lucre.synth.Txn

/** A more complete implementation for tools that process selected children.
  * It implements `handlePress` to update the child selection and then
  * for the currently hit child invoke the `handleSelect` method.
  */
trait BasicCollectionTool[T <: Txn[T], A, Y, Child] extends CollectionToolLike[T, A, Y, Child] {

  protected def handlePress(e: MouseEvent, pos: Long, modelY: Y, childOpt: Option[Child]): Unit = {
    handleMouseSelection(e, childOpt)
    // now go on if region is selected
    childOpt.fold[Unit] {
      handleOutside(e, pos, modelY)
    } { region =>
      if (canvas.selectionModel.contains(region)) handleSelect(e, pos, modelY, region)
    }
  }

  protected def handleSelect (e: MouseEvent, pos: Long, modelY: Y, child: Child): Unit

  protected def handleOutside(e: MouseEvent, pos: Long, modelY: Y): Unit = ()
}
