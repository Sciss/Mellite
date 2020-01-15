/*
 *  DependentMayVeto.scala
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

import de.sciss.lucre.stm.Disposable

/** A dependent that should be consulted first before calling `dispose`. */
trait DependentMayVeto[-Tx] extends Disposable[Tx] {
  /** Consults the dependent about a possible veto on disposal.
    * This does not dispose the dependent, it simply returns the
    * state, which is either `None` (ok to proceed with `dispose`)
    * or some `Veto` instance that should be resolved before calling `dispose`.
    *
    * The normal procedure is to call the veto's `tryResolveVeto` first,
    * and when it returns `true`, to proceed and call `dispose`. If it returns
    * `false`, the disposable should be aborted.
    */
  def prepareDisposal()(implicit tx: Tx): Option[Veto[Tx]]
}
