/*
 *  Application.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite

import de.sciss.desktop.{Preferences, SwingApplication, WindowHandler}
import de.sciss.lucre.stm.Sys
import de.sciss.synth.proc.Workspace

import scala.collection.immutable.{Seq => ISeq}

/** A proxy for a swing application. */
object Application extends SwingApplication { me =>

  type Document = Workspace[_ <: Sys[_]]

  private[this] var peer: Application = _

  private[this] val sync = new AnyRef

  @inline private[this] def requireInitialized(): Unit =
    if (peer == null) throw new IllegalStateException("Application not yet initialized")

  def init(peer: Application): Unit = sync.synchronized {
    if (me.peer != null) throw new IllegalStateException("Trying to initialize application twice")
    me.peer = peer
  }

  def name: String = {
    requireInitialized()
    peer.name
  }

  def userPrefs: Preferences = {
    requireInitialized()
    peer.userPrefs
  }

  def systemPrefs: Preferences = {
    requireInitialized()
    peer.systemPrefs
  }

  def documentHandler: DocumentHandler = {
    requireInitialized()
    peer.documentHandler
  }

  def windowHandler: WindowHandler = {
    requireInitialized()
    peer.windowHandler
  }

  def quit(): Unit = {
    requireInitialized()
    peer.quit()
  }

  def getComponent[A](key: String): Option[A] = {
    requireInitialized()
    peer.getComponent(key)
  }

  def addComponent(key: String, component: Any): Unit = {
    requireInitialized()
    peer.addComponent(key, component)
  }

  def removeComponent(key: String): Unit = {
    requireInitialized()
    peer.removeComponent(key)
  }

  def topLevelObjects : ISeq[String]      = peer.topLevelObjects
  def objectFilter    : String => Boolean = peer.objectFilter
}
trait Application extends SwingApplication {
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
}