/*
 *  Application.scala
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

import de.sciss.desktop.{SwingApplication, SwingApplicationProxy}
import de.sciss.lucre.stm.{Sys, Workspace}

import scala.collection.immutable.{Seq => ISeq}

/** A proxy for a swing application. */
object Application extends SwingApplicationProxy[Workspace[_ <: Sys[_]], Application] { me =>

//  type Document = Workspace[_ <: Sys[_]]
  type Document = Workspace[_ <: Sys[_]]

  def topLevelObjects : ISeq[String]      = peer.topLevelObjects
  def objectFilter    : String => Boolean = peer.objectFilter
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
}