/*
 *  EnsembleFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss
package mellite
package gui

import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.document.{EnsembleFrameImpl => Impl}
import de.sciss.synth.proc.{Ensemble, Universe}

object EnsembleFrame {
  /** Creates a new frame for an ensemble view. */
  def apply[S <: Sys[S]](ensemble: Ensemble[S])
                        (implicit tx: S#Tx, universe: Universe[S]): EnsembleFrame[S] =
    Impl(ensemble)
}

trait EnsembleFrame[S <: Sys[S]] extends lucre.swing.Window[S] {
  def ensembleView: EnsembleView[S]
}