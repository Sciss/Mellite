/*
 *  TimelineToolImpl.scala
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

import de.sciss.desktop.{SwingApplication, SwingApplicationProxy}
import de.sciss.lucre.synth.Server
import de.sciss.synth.Client
import de.sciss.synth.proc.{AuralSystem, Code, Universe}

import scala.collection.immutable.{Seq => ISeq}

/** A proxy for a swing application. */
object Application extends SwingApplicationProxy[Universe[_], Application] { me =>

  type Document = Universe[_]

  def topLevelObjects: ISeq[String] = {
    requireInitialized()
    peer.topLevelObjects
  }

  def objectFilter: String => Boolean = {
    requireInitialized()
    peer.objectFilter
  }

  implicit def auralSystem: AuralSystem = {
    requireInitialized()
    peer.auralSystem
  }

  implicit def compiler: Code.Compiler = {
    requireInitialized()
    peer.compiler
  }

  def applyAudioPreferences(serverCfg: Server.ConfigBuilder, clientCfg: Client.ConfigBuilder,
                            useDevice: Boolean, pickPort: Boolean): Unit = {
    requireInitialized()
    peer.applyAudioPreferences(serverCfg = serverCfg, clientCfg = clientCfg,
      useDevice = useDevice, pickPort = pickPort)
  }

  def cacheDir: File = {
    requireInitialized()
    peer.cacheDir
  }
}
trait Application extends SwingApplication[Application.Document] {
  type Document = Application.Document

  /** A list of object view factories to appear
    * in the top level menu of the GUI.
    *
    * The string indicates the `prefix` of the type
    * (e.g. `"Proc"` or `"Folder"`).
    */
  def topLevelObjects: ISeq[String]

  /** A predicate that tests object view factories for
    * inclusion in the GUI. A `true` value indicates
    * inclusion, a `false` value indicates exclusion.
    *
    * The string indicates the `prefix` of the type
    * (e.g. `"Proc"` or `"Folder"`).
    */
  def objectFilter: String => Boolean

  implicit def auralSystem: AuralSystem

  implicit def compiler: Code.Compiler

  def applyAudioPreferences(serverCfg: Server.ConfigBuilder, clientCfg: Client.ConfigBuilder,
                            useDevice: Boolean, pickPort: Boolean): Unit =
    throw new NotImplementedError()

  def cacheDir: File
}