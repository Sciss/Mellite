/*
 *  Veto.scala
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

import scala.concurrent.Future

/** A trait representing an object that has an objection to a particular action.
  * This objection is manifested through a human readable `vetoMessage` which
  * would generally also be displayed in the GUI when calling `tryResolveVeto`.
  *
  * For example, if trying to close a window that is in dirty state,
  * calling `tryResolveVeto` will display the veto message in a dialog to the user,
  * and the user can confirm the closure, which would result in a `true` return
  * value, or abort the closure, which would result in a `false` return type.
  */
trait Veto[-Tx] {
  def vetoMessage(implicit tx: Tx): String

  /** Attempts to resolve the veto condition by consulting the user.
    *
    * @return successful future if the situation is resolved, e.g. the user agrees to
    *        proceed with the operation. failed future if the veto is upheld, and
    *        the caller should abort the operation.
    */
  def tryResolveVeto()(implicit tx: Tx): Future[Unit]
}