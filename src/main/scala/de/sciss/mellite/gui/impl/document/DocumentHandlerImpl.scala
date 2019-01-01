/*
 *  DocumentHandlerImpl.scala
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

package de.sciss.mellite
package gui
package impl.document

import de.sciss.desktop
import de.sciss.desktop.{Desktop, DocumentHandler => DH}
import de.sciss.lucre.swing.defer
import de.sciss.lucre.synth.Sys
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.Universe

/** We are bridging between the transactional and non-EDT `mellite.DocumentHandler` and
  * the GUI-based `de.sciss.desktop.DocumentHandler`. This is a bit ugly. In theory it
  * should be fine to call into either, as this bridge is backed up by the peer
  * `mellite.DocumentHandler.instance`.
  */
class DocumentHandlerImpl
  extends desktop.DocumentHandler[Mellite.Document]
  with ModelImpl[DH.Update[Mellite.Document]] {

  type Document = Mellite.Document

  private def peer = DocumentHandler.instance

  def addDocument(u: Universe[_]): Unit =
    Mellite.withUniverse(u)(addUniverse(_))

  def removeDocument(u: Document): Unit =
    Mellite.withUniverse(u)(removeUniverse(_))

  private def addUniverse[S <: Sys[S]](u: Universe[S]): Unit =
    u.cursor.step { implicit tx => peer.addDocument(u) }

  private def removeUniverse[S <: Sys[S]](u: Universe[S]): Unit =
    u.cursor.step { implicit tx => u.workspace.dispose() }

  def documents: Iterator[Document] = peer.allDocuments

  private[this] var _active = Option.empty[Document]

  def activeDocument: Option[Document] = _active

  def activeDocument_=(value: Option[Document]): Unit =
    if (_active != value) {
      _active = value
      value.foreach { doc => dispatch(DH.Activated(doc)) }
    }

  peer.addListener {
    case DocumentHandler.Opened(u) => defer {
      Mellite.withUniverse(u)(u1 => dispatch(DH.Added(u1)))
    }
    case DocumentHandler.Closed(u) => defer {
      if (activeDocument.contains(u)) activeDocument = None
      Mellite.withUniverse(u)(u1 => dispatch(DH.Removed(u1)))
    }
  }

  Desktop.addQuitAcceptor(ActionCloseAllWorkspaces.tryCloseAll())
}
