/*
 *  NuagesEditorView.scala
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

package de.sciss.mellite
package gui

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm.Sys
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.{Sys => SSys}
import de.sciss.nuages.Nuages
import de.sciss.synth.proc.Universe
import de.sciss.synth.proc.gui.UniverseView
import impl.document.{NuagesEditorViewImpl => Impl}

import scala.swing.Action

object NuagesEditorView {
  def apply[S <: SSys[S]](obj: Nuages[S])(implicit tx: S#Tx, universe: Universe[S],
                                          undoManager: UndoManager): NuagesEditorView[S] =
    Impl(obj)

  /** Key convention for a `BooleanObj` in the `Nuages` object
    * to determine whether to create solo function or not. If it evaluates
    * to `true`, the solo channels are taken from Mellite's headphones preferences.
    */
  final val attrUseSolo = "use-solo"

  /** Key convention for an `IntVector` in the `Nuages` object
    * to determine the master channels. If not found, the master channels
    * are taken from Mellite's number-of-audio-outputs preferences.
    */
  final val attrMasterChans = "master-channels"

  /** Key convention for a `Folder` in the `Nuages` object
    * containing instances of `IntVector`. Each `IntVector` is translated
    * into a `NamedBusConfig` (using the object's name attribute).

    * These configurations are used for the microphone inputs.
    */
  final val attrMicInputs = "mic-inputs"

  /** Key convention for a `Folder` in the `Nuages` object
    * containing instances of `IntVector`. Each `IntVector` is translated
    * into a `NamedBusConfig` (using the object's name attribute).

    * These configurations are used for the line inputs.
    */
  final val attrLineInputs = "line-inputs"

  /** Key convention for a `Folder` in the `Nuages` object
    * containing instances of `IntVector`. Each `IntVector` is translated
    * into a `NamedBusConfig` (using the object's name attribute).

    * These configurations are used for the line outputs.
    */
  final val attrLineOutputs = "line-outputs"
}
trait NuagesEditorView[S <: Sys[S]] extends UniverseView[S] with View.Editable[S] with CanBounce {
  def actionDuplicate: Action
}